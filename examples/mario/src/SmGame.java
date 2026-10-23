import jnative.graphics.*;
import jnative.collections.*;

/**
 * Game rules, simulation (fixed 60 Hz step) and rendering for the side-scrolling platformer.
 * Logical resolution 256x240; the canvas shows it at SCALE x.
 *
 * @author 张晓达
 */
public class SmGame {
    static int SCREEN_W = 256;
    static int SCREEN_H = 240;

    // game states
    static int TITLE = 0;
    static int PLAYING = 1;
    static int CLEAR = 3;
    static int GAME_OVER = 4;
    static int WIN = 5;
    static int INTRO = 6;

    int state = 0;
    SmLevel level;
    SmPlayer player;
    ArrayList<SmEnemy> enemies = new ArrayList<SmEnemy>();
    ArrayList<SmItem> items = new ArrayList<SmItem>();
    ArrayList<SmFireball> fireballs = new ArrayList<SmFireball>();
    int levelIndex;
    int warpTarget;
    int warpTopPx;
    int prevRun;

    int[] keys = new int[512];
    int prevJump;
    int jumpBuffer;
    int coyote;
    /** Frames the "WORLD x-y" card stays up (0 skips it, used by tests). */
    int introLen = 120;
    int introTimer;
    int camX;
    int score;
    int coins;
    int lives = 3;
    int time = 400;
    int timeTick;
    long frame;
    int freeze;
    int deathTimer;
    int clearTimer;
    int topScore;
    int paused;
    int flagGrabY;
    int flagPoints;
    int flagSlideDone;
    int worldNo = 1;
    int stageNo = 1;

    Font hudFont = new Font("Menlo", 17, FontStyle.BOLD);
    Font titleFont = new Font("Menlo", 48, FontStyle.BOLD);
    Font bigFont = new Font("Menlo", 30, FontStyle.BOLD);
    Font smallFont = new Font("Menlo", 13, FontStyle.BOLD);

    void newGame() {
        score = 0;
        coins = 0;
        lives = 3;
        levelIndex = 0;
        stageNo = 1;
        loadLevel();
        startIntro();
    }

    void startIntro() {
        SmAudio.musicStop();
        if (introLen > 0) {
            state = INTRO;
            introTimer = 0;
        } else {
            beginPlay();
        }
    }

    void beginPlay() {
        state = PLAYING;
        SmAudio.musicStartTrack(level.theme);
    }

    void loadLevel() {
        level = SmLevel.forIndex(levelIndex);
        stageNo = levelIndex + 1;
        player = new SmPlayer();
        jumpBuffer = 0;
        coyote = 0;
        player.x = level.startX * 16 + 2;
        player.y = level.startY * 16;
        enemies.clear();
        items.clear();
        fireballs.clear();
        prevRun = 0;
        camX = 0;
        time = 400;
        timeTick = 0;
        freeze = 0;
        flagSlideDone = 0;
        deathTimer = 0;
        clearTimer = 0;
        int i = 0;
        while (i < level.spawnCount) {
            SmEnemy e = new SmEnemy(level.spawnKind[i], level.spawnX[i] * 16, level.spawnY[i] * 16);
            enemies.add(e);
            i = i + 1;
        }
    }

    /** Held-state of a key; letters match either case (Cocoa reports them lower-case). */
    int key(int code) {
        if (code < 0 || code >= 512) {
            return 0;
        }
        if (keys[code] != 0) {
            return 1;
        }
        if (code >= 65 && code <= 90 && keys[code + 32] != 0) {
            return 1;
        }
        return 0;
    }

    void setKey(int code, int down) {
        if (code >= 0 && code < 512) {
            keys[code] = down;
        }
    }

    /** Key event from the window: also derives Shift (run) from the modifier bits. */
    void onKey(int code, int mods, int down) {
        setKey(code, down);
        if (down != 0) {
            keys[16] = mods % 2;
        }
    }

    // ---------------------------------------------------------------- simulation

    void update() {
        frame = frame + 1;
        if (state == TITLE) {
            if (key(13) != 0 || key(32) != 0) {
                keys[13] = 0;
                keys[32] = 0;
                newGame();
            }
            return;
        }
        if (state == GAME_OVER) {
            clearTimer = clearTimer + 1;
            if (clearTimer > 60 && (key(13) != 0 || key(32) != 0)) {
                keys[13] = 0;
                keys[32] = 0;
                state = TITLE;
            }
            return;
        }
        if (state == INTRO) {
            introTimer = introTimer + 1;
            if (introTimer >= introLen || (introTimer > 20 && (key(13) != 0 || key(32) != 0))) {
                keys[13] = 0;
                keys[32] = 0;
                beginPlay();
            }
            return;
        }
        if (state == WIN) {
            clearTimer = clearTimer + 1;
            if (clearTimer > 90 && (key(13) != 0 || key(32) != 0)) {
                keys[13] = 0;
                keys[32] = 0;
                state = TITLE;
            }
            return;
        }
        if (state == CLEAR) {
            updateClear();
            return;
        }
        if (key(80) != 0 || key(27) != 0) {
            keys[80] = 0;
            keys[27] = 0;
            paused = 1 - paused;
        }
        if (paused != 0) {
            return;
        }
        level.tickBumps();
        if (freeze > 0) {
            freeze = freeze - 1;
            updatePlayerStateWhileFrozen();
            return;
        }
        if (player.state == 7) {
            updatePipeSink();
            return;
        }
        if (player.state == 8) {
            updatePipeRise();
            return;
        }
        if (player.state == 3) {
            updateDying();
            return;
        }
        if (player.state == 4) {
            updateFlagSlide();
            return;
        }
        if (player.state == 5) {
            updateWalkToCastle();
            return;
        }
        updateAlive();
    }

    void updatePlayerStateWhileFrozen() {
        if (freeze == 0) {
            if (player.state == 1 || player.state == 2) {
                player.state = 0;
            }
        }
    }

    void updateAlive() {
        int left = 0;
        if (key(37) != 0 || key(65) != 0) {
            left = 1;
        }
        int right = 0;
        if (key(39) != 0 || key(68) != 0) {
            right = 1;
        }
        int run = 0;
        if (key(88) != 0 || key(74) != 0 || key(16) != 0) {
            run = 1;
        }
        int jump = 0;
        if (key(90) != 0 || key(32) != 0 || key(38) != 0 || key(87) != 0 || key(75) != 0) {
            jump = 1;
        }
        int jumpPressed = 0;
        if (jump != 0 && prevJump == 0) {
            jumpPressed = 1;
        }
        prevJump = jump;
        int down = 0;
        if (key(40) != 0 || key(83) != 0) {
            down = 1;
        }
        // jump buffer (press just before landing) and coyote time (just after leaving a ledge)
        if (jumpPressed != 0) {
            jumpBuffer = 6;
        } else if (jumpBuffer > 0) {
            jumpBuffer = jumpBuffer - 1;
        }
        if (player.onGround != 0) {
            coyote = 4;
        } else if (coyote > 0) {
            coyote = coyote - 1;
        }
        jumpPressed = 0;
        if (jumpBuffer > 0 && jump != 0
                && (player.onGround != 0 || (coyote > 0 && player.vy >= 0 && player.jumping == 0))) {
            jumpPressed = 1;
            player.onGround = 1;
            jumpBuffer = 0;
            coyote = 0;
            SmAudio.play(0);
        }
        if (player.onGround != 0 && player.skidding != 0 && frame % 3 == 0) {
            spawnDust(player.x + player.w / 2.0 - player.facing * 4, player.y + player.h - 4);
        }
        if (run != 0 && prevRun == 0 && player.fire != 0 && player.big != 0 && fireballs.size() < 2) {
            shootFireball();
        }
        prevRun = run;
        if (player.star > 0) {
            player.star = player.star - 1;
            if (player.star == 0) {
                SmAudio.musicStartTrack(trackHere());
            }
        }
        player.update(level, left, right, run, jump, jumpPressed, down);

        if (down != 0 && player.onGround != 0 && player.state == 0) {
            tryEnterPipe();
        }

        // left camera wall
        if (player.x < camX) {
            player.x = camX;
            if (player.vx < 0) {
                player.vx = 0;
            }
        }
        // camera follows to the right only
        double target = player.x - 112;
        if (target > camX) {
            camX = (int) target;
        }
        int maxCam = level.cols * 16 - SCREEN_W;
        if (camX > maxCam) {
            camX = maxCam;
        }
        if (camX < 0) {
            camX = 0;
        }

        if (player.bumpedHead != 0) {
            hitBlock(player.bumpTx, player.bumpTy);
        }
        collectCoinTiles();
        checkFlag();

        updateEnemies();
        updateItems();
        updateFireballs();
        playerVsEnemies();

        // clock
        timeTick = timeTick + 1;
        if (timeTick >= 24) {
            timeTick = 0;
            time = time - 1;
            if (time <= 0) {
                time = 0;
                kill();
            }
        }
        if (player.y > SCREEN_H + 24 && player.state == 0) {
            kill();
        }
    }

    // ---- blocks

    void hitBlock(int tx, int ty) {
        char c = level.get(tx, ty);
        if (c == '?') {
            level.set(tx, ty, 'u');
            level.startBump(tx, ty);
            SmItem coin = new SmItem(1, tx * 16 + 2, ty * 16 - 16);
            coin.vy = -5.0;
            coin.life = 34;
            items.add(coin);
            coins = coins + 1;
            addScore(200);
            SmAudio.play(1);
            if (coins >= 100) {
                coins = 0;
                lives = lives + 1;
                SmAudio.play(7);
            }
            knockEnemiesOn(tx, ty);
        } else if (c == 'M' || c == 'S') {
            level.set(tx, ty, 'u');
            level.startBump(tx, ty);
            int mk = 0;
            if (c == 'S') {
                mk = 6;
            } else if (player.big != 0) {
                mk = 4;
            }
            SmItem m = new SmItem(mk, tx * 16 + 2, ty * 16);
            m.emerge = 16;
            m.startY = ty * 16 - 16;
            m.vx = 0;
            m.life = 1;
            items.add(m);
            SmAudio.play(2);
            knockEnemiesOn(tx, ty);
        } else if (c == 'B') {
            if (player.big != 0) {
                level.set(tx, ty, '.');
                addScore(50);
                SmAudio.play(3);
                spawnDebris(tx, ty);
            } else {
                level.startBump(tx, ty);
                SmAudio.play(4);
            }
            knockEnemiesOn(tx, ty);
        } else {
            SmAudio.play(4);
        }
    }

    void spawnDebris(int tx, int ty) {
        int i = 0;
        while (i < 4) {
            SmItem d = new SmItem(3, tx * 16 + (i % 2) * 8, ty * 16 + (i / 2) * 8);
            d.w = 8;
            d.h = 8;
            if (i % 2 == 0) {
                d.vx = -1.5;
            } else {
                d.vx = 1.5;
            }
            if (i < 2) {
                d.vy = -6.0;
            } else {
                d.vy = -4.0;
            }
            d.life = 120;
            items.add(d);
            i = i + 1;
        }
    }

    void knockEnemiesOn(int tx, int ty) {
        int i = 0;
        while (i < enemies.size()) {
            SmEnemy e = enemies.get(i);
            if (e.dead == 0 && e.squash == 0) {
                double cx = e.x + e.w / 2.0;
                if (Math.abs(cx - (tx * 16 + 8)) < 12 && Math.abs(e.y + e.h - ty * 16) < 4) {
                    killEnemy(e, 100);
                }
            }
            i = i + 1;
        }
    }

    void collectCoinTiles() {
        int tx0 = SmBody.tileOf(player.x);
        int tx1 = SmBody.tileOf(player.x + player.w - 0.01);
        int ty0 = SmBody.tileOf(player.y);
        int ty1 = SmBody.tileOf(player.y + player.h - 0.01);
        int ty = ty0;
        while (ty <= ty1) {
            int tx = tx0;
            while (tx <= tx1) {
                if (level.get(tx, ty) == 'o') {
                    level.set(tx, ty, '.');
                    coins = coins + 1;
                    addScore(200);
                    SmAudio.play(1);
                    if (coins >= 100) {
                        coins = 0;
                        lives = lives + 1;
                        SmAudio.play(7);
                    }
                }
                tx = tx + 1;
            }
            ty = ty + 1;
        }
    }

    // ---- flag

    void checkFlag() {
        int fx = level.flagX * 16;
        if (player.x + player.w > fx + 6 && player.x < fx + 10) {
            if (player.y < 12 * 16 && player.state == 0) {
                player.state = 4;
                player.vx = 0;
                player.vy = 0;
                player.x = fx - 5;
                flagGrabY = (int) (player.y + player.h);
                int h = 13 * 16 - flagGrabY;
                int pts = 100;
                if (h > 16) {
                    pts = 400;
                }
                if (h > 48) {
                    pts = 800;
                }
                if (h > 96) {
                    pts = 2000;
                }
                if (h > 130) {
                    pts = 5000;
                }
                flagPoints = pts;
                addScore(pts);
                SmAudio.musicStop();
                SmAudio.play(6);
                flagSlideDone = 0;
            }
        }
    }

    void updateFlagSlide() {
        if (player.y + player.h < 12 * 16) {
            player.y = player.y + 2;
        } else {
            player.y = 12 * 16 - player.h;
            player.stateTimer = player.stateTimer + 1;
            if (player.stateTimer > 20) {
                player.state = 5;
                player.stateTimer = 0;
                player.x = level.flagX * 16 + 12;
                player.facing = 1;
                player.vx = 0;
                player.vy = 0;
            }
        }
    }

    void updateWalkToCastle() {
        player.vx = 1.0;
        player.vy = player.vy + 0.4375;
        if (player.vy > 4) {
            player.vy = 4;
        }
        player.step(level, 1);
        player.walkTimer = player.walkTimer + 1;
        if (player.walkTimer >= 6) {
            player.walkTimer = 0;
            player.walkFrame = player.walkFrame + 1;
            if (player.walkFrame >= 3) {
                player.walkFrame = 0;
            }
        }
        double target = player.x - 112;
        int maxCam = level.cols * 16 - SCREEN_W;
        if (target > camX) {
            camX = (int) target;
        }
        if (camX > maxCam) {
            camX = maxCam;
        }
        if (player.x > level.endX - 8) {
            state = CLEAR;
            clearTimer = 0;
            player.state = 6;
        }
    }

    void updateClear() {
        clearTimer = clearTimer + 1;
        // convert remaining time into points
        if (time > 0 && clearTimer > 30) {
            int take = 1;
            time = time - take;
            addScore(50 * take);
            if (clearTimer % 3 == 0) {
                SmAudio.play(1);
            }
        } else if (time == 0 && clearTimer > 150) {
            if (key(13) != 0 || key(32) != 0) {
                keys[13] = 0;
                keys[32] = 0;
                if (score > topScore) {
                    topScore = score;
                }
                if (levelIndex + 1 < SmLevel.LEVEL_COUNT) {
                    nextLevel();
                } else {
                    state = WIN;
                    clearTimer = 0;
                    SmAudio.play(7);
                }
            }
        }
    }

    void nextLevel() {
        int big = player.big;
        int fire = player.fire;
        levelIndex = levelIndex + 1;
        loadLevel();
        player.setBig(big);
        player.fire = fire;
        startIntro();
    }

    // ---- enemies

    void updateEnemies() {
        int i = 0;
        while (i < enemies.size()) {
            SmEnemy e = enemies.get(i);
            if (e.active == 0) {
                if (e.x < camX + SCREEN_W + 16) {
                    e.active = 1;
                    e.dir = -1;
                    e.vx = e.dir * 0.5;
                }
            }
            if (e.active != 0) {
                updateEnemy(e);
            }
            if (e.removed != 0) {
                enemies.remove(i);
            } else {
                i = i + 1;
            }
        }
    }

    void updateEnemy(SmEnemy e) {
        e.anim = e.anim + 1;
        if (e.squash > 0) {
            e.squash = e.squash - 1;
            if (e.squash == 0) {
                e.removed = 1;
            }
            return;
        }
        if (e.dead != 0) {
            e.vy = e.vy + 0.4;
            e.y = e.y + e.vy;
            e.x = e.x + e.vx;
            if (e.y > SCREEN_H + 40) {
                e.removed = 1;
            }
            return;
        }
        if (e.kickGrace > 0) {
            e.kickGrace = e.kickGrace - 1;
        }
        if (e.kind == 0 || e.kind == 1) {
            e.vx = e.dir * 0.5;
        } else if (e.kind == 2) {
            e.vx = 0;
        } else {
            e.vx = e.dir * 3.0;
        }
        e.vy = e.vy + 0.4;
        if (e.vy > 4.0) {
            e.vy = 4.0;
        }
        e.step(level, 1);
        if (e.hitWall != 0) {
            e.dir = -e.dir;
            if (e.kind == 3) {
                SmAudio.play(4);
            }
        }
        if (e.y > SCREEN_H + 40 || e.x < camX - 64) {
            e.removed = 1;
        }
        // sliding shells flatten everything they touch
        if (e.kind == 3) {
            int j = 0;
            while (j < enemies.size()) {
                SmEnemy o = enemies.get(j);
                if (o != e && o.active != 0 && o.dead == 0 && o.squash == 0 && e.overlaps(o) != 0) {
                    killEnemy(o, 500);
                }
                j = j + 1;
            }
        } else if (e.kind == 0 || e.kind == 1) {
            // walkers bounce off each other
            int j = 0;
            while (j < enemies.size()) {
                SmEnemy o = enemies.get(j);
                if (o != e && o.active != 0 && o.dead == 0 && o.squash == 0
                        && (o.kind == 0 || o.kind == 1) && e.overlaps(o) != 0) {
                    if (e.x < o.x) {
                        e.dir = -1;
                        e.x = e.x - 0.6;
                    } else {
                        e.dir = 1;
                        e.x = e.x + 0.6;
                    }
                }
                j = j + 1;
            }
        }
    }

    void killEnemy(SmEnemy e, int points) {
        e.dead = 1;
        e.vy = -3.0;
        e.vx = 0.8;
        if (player.x + player.w / 2.0 > e.x + e.w / 2.0) {
            e.vx = -0.8;
        }
        addScore(points);
        popup(e.x, e.y, points);
        SmAudio.play(5);
    }

    void playerVsEnemies() {
        if (player.state != 0) {
            return;
        }
        int i = 0;
        while (i < enemies.size()) {
            SmEnemy e = enemies.get(i);
            i = i + 1;
            if (e.active == 0 || e.dead != 0 || e.squash > 0) {
                continue;
            }
            if (player.overlaps(e) == 0) {
                continue;
            }
            double feet = player.y + player.h;
            int stomp = 0;
            if (player.vy > 0.1 && feet - e.y < 10.0) {
                stomp = 1;
            }
            if (player.star > 0) {
                killEnemy(e, 200);
                continue;
            }
            if (e.kind == 2) {
                // resting shell: any touch kicks it
                if (e.kickGrace == 0) {
                    kickShell(e, stomp);
                }
                continue;
            }
            if (stomp != 0) {
                stompEnemy(e);
            } else if (e.kind == 3 && e.kickGrace > 0) {
                continue;
            } else {
                hurt();
            }
        }
    }

    void stompEnemy(SmEnemy e) {
        player.stompCombo = player.stompCombo + 1;
        int pts = comboPoints(player.stompCombo);
        if (pts < 0) {
            lives = lives + 1;
            SmAudio.play(7);
            pts = 0;
        } else {
            addScore(pts);
            popup(e.x, e.y - 8, pts);
        }
        if (key(90) != 0 || key(32) != 0 || key(38) != 0 || key(87) != 0 || key(75) != 0) {
            player.vy = -4.0;
        } else {
            player.vy = -3.0;
        }
        player.y = e.y - player.h;
        SmAudio.play(8);
        if (e.kind == 0) {
            e.squash = 24;
            e.vx = 0;
        } else if (e.kind == 1) {
            e.kind = 2;
            e.vx = 0;
            e.kickGrace = 10;
            e.h = 14;
        } else if (e.kind == 3) {
            e.kind = 2;
            e.vx = 0;
            e.kickGrace = 10;
        }
    }

    int comboPoints(int n) {
        if (n == 1) {
            return 100;
        }
        if (n == 2) {
            return 200;
        }
        if (n == 3) {
            return 400;
        }
        if (n == 4) {
            return 500;
        }
        if (n == 5) {
            return 800;
        }
        if (n == 6) {
            return 1000;
        }
        if (n == 7) {
            return 2000;
        }
        if (n == 8) {
            return 4000;
        }
        if (n == 9) {
            return 8000;
        }
        return -1;
    }

    void kickShell(SmEnemy e, int stomp) {
        e.kind = 3;
        if (player.x + player.w / 2.0 < e.x + e.w / 2.0) {
            e.dir = 1;
        } else {
            e.dir = -1;
        }
        e.kickGrace = 12;
        addScore(400);
        popup(e.x, e.y - 8, 400);
        SmAudio.play(8);
        if (stomp != 0) {
            player.vy = -3.0;
            player.y = e.y - player.h;
        }
    }

    void hurt() {
        if (player.invincible > 0 || player.state != 0 || player.star > 0) {
            return;
        }
        if (player.big != 0) {
            player.setBig(0);
            player.fire = 0;
            player.crouching = 0;
            player.h = 16;
            player.invincible = 120;
            player.state = 2;
            freeze = 40;
            SmAudio.play(9);
        } else {
            kill();
        }
    }

    void kill() {
        if (player.state == 3) {
            return;
        }
        player.state = 3;
        player.stateTimer = 0;
        player.vx = 0;
        player.vy = 0;
        deathTimer = 0;
        SmAudio.musicStop();
        SmAudio.play(10);
    }

    void updateDying() {
        deathTimer = deathTimer + 1;
        if (deathTimer == 24) {
            player.vy = -5.5;
        }
        if (deathTimer > 24) {
            player.vy = player.vy + 0.25;
            if (player.vy > 5) {
                player.vy = 5;
            }
            player.y = player.y + player.vy;
        }
        if (deathTimer > 200) {
            lives = lives - 1;
            if (lives <= 0) {
                state = GAME_OVER;
                clearTimer = 0;
                if (score > topScore) {
                    topScore = score;
                }
            } else {
                loadLevel();
                startIntro();
            }
        }
    }

    // ---- items

    void updateItems() {
        int i = 0;
        while (i < items.size()) {
            SmItem it = items.get(i);
            int remove = 0;
            if (it.kind == 0) {
                remove = updateMushroom(it);
            } else if (it.kind == 4) {
                remove = updateFlower(it);
            } else if (it.kind == 6) {
                remove = updateStar(it);
            } else if (it.kind == 7) {
                it.y = it.y - 0.25;
                it.life = it.life - 1;
                if (it.life <= 0) {
                    remove = 1;
                }
            } else if (it.kind == 5) {
                it.life = it.life - 1;
                if (it.life <= 0) {
                    remove = 1;
                }
            } else if (it.kind == 1) {
                it.vy = it.vy + 0.45;
                it.y = it.y + it.vy;
                it.frame = it.frame + 1;
                it.life = it.life - 1;
                if (it.life <= 0) {
                    remove = 1;
                    popup(it.x, it.y, 200);
                }
            } else if (it.kind == 2) {
                it.y = it.y - 0.7;
                it.life = it.life - 1;
                if (it.life <= 0) {
                    remove = 1;
                }
            } else if (it.kind == 3) {
                it.vy = it.vy + 0.45;
                it.x = it.x + it.vx;
                it.y = it.y + it.vy;
                it.life = it.life - 1;
                if (it.life <= 0 || it.y > SCREEN_H + 20) {
                    remove = 1;
                }
            }
            if (remove != 0) {
                items.remove(i);
            } else {
                i = i + 1;
            }
        }
    }

    int updateMushroom(SmItem m) {
        if (m.emerge > 0) {
            m.emerge = m.emerge - 1;
            m.y = m.y - 1;
            if (m.emerge == 0) {
                m.vx = 1.0;
            }
            return 0;
        }
        m.vy = m.vy + 0.35;
        if (m.vy > 4) {
            m.vy = 4;
        }
        m.step(level, 1);
        if (m.hitWall != 0) {
            m.vx = -m.vx;
            if (m.vx == 0) {
                m.vx = 1.0 * m.hitWall * -1;
            }
        }
        if (m.vx == 0) {
            m.vx = 1.0;
        }
        if (m.y > SCREEN_H + 20) {
            return 1;
        }
        if (player.state == 0 && player.overlaps(m) != 0) {
            collectMushroom();
            return 1;
        }
        return 0;
    }

    int updateFlower(SmItem f) {
        f.frame = f.frame + 1;
        if (f.emerge > 0) {
            f.emerge = f.emerge - 1;
            f.y = f.y - 1;
            return 0;
        }
        if (player.state == 0 && player.overlaps(f) != 0) {
            collectFlower();
            return 1;
        }
        return 0;
    }

    /** Tune that belongs to where the hero is (underground levels and the bonus room are dark). */
    int trackHere() {
        return dark();
    }

    int dark() {
        if (level.theme == 1) {
            return 1;
        }
        if (level.bonusStart > 0 && player.x >= level.bonusStart * 16) {
            return 1;
        }
        return 0;
    }

    void tryEnterPipe() {
        int tx = SmBody.tileOf(player.x + player.w / 2.0);
        int ty = SmBody.tileOf(player.y + player.h + 0.5);
        char c = level.get(tx, ty);
        if (c != 'a' && c != 'b') {
            return;
        }
        int left = tx;
        if (c == 'b') {
            left = tx - 1;
        }
        if (Math.abs(player.x + player.w / 2.0 - (left * 16 + 16)) > 7) {
            return;
        }
        int i = 0;
        while (i < level.warpCount) {
            if (level.warpSrc[i] == left) {
                warpTarget = level.warpDst[i];
                player.state = 7;
                player.stateTimer = 0;
                player.vx = 0;
                player.vy = 0;
                player.crouching = 0;
                player.x = left * 16 + 16 - player.w / 2.0;
                SmAudio.play(9);
                return;
            }
            i = i + 1;
        }
    }

    void updatePipeSink() {
        player.y = player.y + 1;
        player.stateTimer = player.stateTimer + 1;
        if (player.stateTimer >= player.h) {
            int top = level.pipeTop(warpTarget);
            player.vx = 0;
            player.vy = 0;
            player.stateTimer = 0;
            int maxCam = level.cols * 16 - SCREEN_W;
            if (top >= 0) {
                player.x = warpTarget * 16 + 16 - player.w / 2.0;
                player.y = top * 16;
                warpTopPx = top * 16;
                player.state = 8;
                camX = warpTarget * 16 - 112;
                if (camX > maxCam) {
                    camX = maxCam;
                }
                if (camX < 0) {
                    camX = 0;
                }
            } else {
                player.x = warpTarget * 16 + 2;
                player.y = 13 * 16 - player.h;
                player.state = 0;
                player.invincible = 20;
                camX = level.bonusStart * 16;
                prevJump = 1;
            }
            prevRun = 1;
            if (player.star == 0) {
                SmAudio.musicStartTrack(trackHere());
            }
        }
    }

    void updatePipeRise() {
        player.stateTimer = player.stateTimer + 1;
        player.y = warpTopPx - player.stateTimer;
        if (player.stateTimer >= player.h) {
            player.y = warpTopPx - player.h;
            player.state = 0;
            player.vy = 0;
            player.invincible = 20;
            prevJump = 1;
        }
    }

    int updateStar(SmItem m) {
        m.frame = m.frame + 1;
        if (m.emerge > 0) {
            m.emerge = m.emerge - 1;
            m.y = m.y - 1;
            if (m.emerge == 0) {
                m.vx = 1.3;
                m.vy = -3.5;
            }
            return 0;
        }
        m.vy = m.vy + 0.25;
        if (m.vy > 4) {
            m.vy = 4;
        }
        m.step(level, 1);
        if (m.onGround != 0) {
            m.vy = -4.2;
        }
        if (m.hitWall != 0) {
            m.vx = -m.hitWall * 1.3;
        }
        if (m.vx == 0) {
            m.vx = 1.3;
        }
        if (m.y > SCREEN_H + 20) {
            return 1;
        }
        if (player.state == 0 && player.overlaps(m) != 0) {
            addScore(1000);
            popup(player.x, player.y - 8, 1000);
            SmAudio.play(11);
            player.star = 600;
            SmAudio.musicStartTrack(2);
            return 1;
        }
        return 0;
    }

    void collectFlower() {
        addScore(1000);
        popup(player.x, player.y - 8, 1000);
        SmAudio.play(11);
        if (player.big == 0) {
            player.setBig(1);
            player.state = 1;
            freeze = 40;
        }
        player.fire = 1;
    }

    void shootFireball() {
        double fx = player.x + player.w;
        if (player.facing < 0) {
            fx = player.x - 8;
        }
        double fy = player.y + 10;
        if (player.crouching != 0) {
            fy = player.y + 8;
        }
        SmFireball f = new SmFireball(fx, fy, player.facing);
        fireballs.add(f);
        SmAudio.play(5);
    }

    void spawnDust(double px, double py) {
        SmItem d = new SmItem(7, px - 4, py - 4);
        d.w = 8;
        d.h = 8;
        d.life = 14;
        items.add(d);
    }

    void spark(double px, double py) {
        SmItem sp = new SmItem(5, px - 4, py - 4);
        sp.w = 8;
        sp.h = 8;
        sp.life = 8;
        items.add(sp);
    }

    void updateFireballs() {
        int i = 0;
        while (i < fireballs.size()) {
            SmFireball f = fireballs.get(i);
            f.anim = f.anim + 1;
            f.vx = f.dir * 3.5;
            f.vy = f.vy + 0.4;
            if (f.vy > 4.0) {
                f.vy = 4.0;
            }
            f.step(level, 1);
            if (f.onGround != 0) {
                f.vy = -3.0;
            }
            if (f.hitWall != 0 || f.bumpedHead != 0) {
                f.dead = 1;
            }
            // enemies: fire kills walkers, bounces off shells
            int j = 0;
            while (j < enemies.size() && f.dead == 0) {
                SmEnemy e = enemies.get(j);
                if (e.active != 0 && e.dead == 0 && e.squash == 0 && f.overlaps(e) != 0) {
                    f.dead = 1;
                    if (e.kind == 0 || e.kind == 1) {
                        killEnemy(e, 200);
                    } else {
                        SmAudio.play(4);
                    }
                }
                j = j + 1;
            }
            if (f.x < camX - 16 || f.x > camX + SCREEN_W + 16 || f.y > SCREEN_H + 16) {
                f.removed = 1;
            }
            if (f.dead != 0) {
                spark(f.x + 4, f.y + 4);
                f.removed = 1;
            }
            if (f.removed != 0) {
                fireballs.remove(i);
            } else {
                i = i + 1;
            }
        }
    }

    void collectMushroom() {
        addScore(1000);
        popup(player.x, player.y - 8, 1000);
        SmAudio.play(11);
        if (player.big == 0) {
            player.setBig(1);
            player.state = 1;
            freeze = 40;
        }
    }

    void popup(double px, double py, int pts) {
        SmItem p = new SmItem(2, px, py);
        p.value = pts;
        p.life = 40;
        items.add(p);
    }

    void addScore(int pts) {
        score = score + pts;
    }

    // ---------------------------------------------------------------- rendering

    void drawText(Graphics g, String text, int sx, int sy, Font f) {
        g.setFont(f);
        g.setColor(Color.BLACK);
        g.drawText(text, sx + 2, sy + 2);
        g.setColor(Color.WHITE);
        g.drawText(text, sx, sy);
    }

    void drawCentered(Graphics g, String text, int sy, Font f) {
        int w = g.measureText(text, f).width;
        drawText(g, text, (SCREEN_W * SmSprites.SCALE - w) / 2, sy, f);
    }

    String pad(int v, int width) {
        String s = String.valueOf(v);
        while (s.length() < width) {
            s = "0" + s;
        }
        return s;
    }

    void paint(Graphics g) {
        int S = SmSprites.SCALE;
        if (level != null && state != TITLE && dark() != 0) {
            g.setColor(new Color(0, 0, 0));
        } else {
            g.setColor(new Color(92, 148, 252));
        }
        g.fillRect(0, 0, SCREEN_W * S, SCREEN_H * S);
        if (state == TITLE) {
            paintTitle(g);
            return;
        }
        if (state == INTRO) {
            g.setColor(new Color(0, 0, 0));
            g.fillRect(0, 0, SCREEN_W * S, SCREEN_H * S);
            paintHud(g);
            drawCentered(g, "WORLD " + worldNo + "-" + stageNo, 96 * S, bigFont);
            g.drawImage(SmSprites.heroR[0], 104 * S, 120 * S);
            drawText(g, "x  " + lives, 128 * S, 136 * S, bigFont);
            if (level.theme == 1) {
                drawCentered(g, "UNDERGROUND", 176 * S, smallFont);
            }
            return;
        }
        if (dark() == 0) {
            paintScenery(g);
        }
        paintItemsBehind(g);
        if (player.state == 7 || player.state == 8) {
            paintPlayer(g);
        }
        paintTiles(g);
        paintEntities(g);
        paintHud(g);
        if (state == GAME_OVER) {
            drawCentered(g, "GAME OVER", 112 * S, bigFont);
        }
        if (state == CLEAR) {
            drawCentered(g, "COURSE CLEAR!", 100 * S, bigFont);
            if (time == 0 && clearTimer > 150) {
                if (levelIndex + 1 < SmLevel.LEVEL_COUNT) {
                    drawCentered(g, "ENTER: NEXT COURSE", 130 * S, hudFont);
                } else {
                    drawCentered(g, "PRESS ENTER", 130 * S, hudFont);
                }
            }
        }
        if (state == WIN) {
            drawCentered(g, "YOU WIN!", 90 * S, bigFont);
            drawCentered(g, "THANK YOU FOR PLAYING", 120 * S, hudFont);
            drawCentered(g, "SCORE " + pad(score, 6), 146 * S, hudFont);
            if (clearTimer > 90) {
                drawCentered(g, "PRESS ENTER", 176 * S, hudFont);
            }
        }
        if (paused != 0) {
            drawCentered(g, "PAUSED", 112 * S, bigFont);
        }
    }

    void paintTitle(Graphics g) {
        int S = SmSprites.SCALE;
        camX = (int) ((frame / 2) % 768);
        paintScenery(g);
        int off = camX % 16;
        int x = 0;
        while (x <= 16) {
            g.drawImage(SmSprites.ground, (x * 16 - off) * S, 13 * 16 * S);
            g.drawImage(SmSprites.ground, (x * 16 - off) * S, 14 * 16 * S);
            x = x + 1;
        }
        int hf = 1 + (int) ((frame / 5) % 2);
        g.drawImage(SmSprites.heroR[hf], 40 * S, 12 * 16 * S);
        g.drawImage(SmSprites.goomba[(int) ((frame / 10) % 2)], 180 * S, 12 * 16 * S);
        drawCentered(g, "JNATIVE", 50 * S, titleFont);
        drawCentered(g, "RUNNER", 90 * S, titleFont);
        if ((frame / 30) % 2 == 0) {
            drawCentered(g, "ENTER: START", 128 * S, hudFont);
        }
        drawCentered(g, "ARROWS/AD MOVE  Z/SPACE/K JUMP", 150 * S, smallFont);
        drawCentered(g, "X/SHIFT/J RUN+FIRE  P PAUSE", 164 * S, smallFont);
        drawCentered(g, "TOP " + pad(topScore, 6), 190 * S, hudFont);
        camX = 0;
    }

    void paintScenery(Graphics g) {
        int S = SmSprites.SCALE;
        int cam = camX;
        // repeating decoration pattern every 768 px
        int base = (cam / 768) * 768 - 768;
        int rep = 0;
        while (rep < 3) {
            int bx = base + rep * 768;
            drawDeco(g, SmSprites.hill, bx + 0, 13 * 16 - 8 * 1, cam);
            drawDeco(g, SmSprites.bush, bx + 176, 13 * 16 - 6, cam);
            drawDeco(g, SmSprites.cloud, bx + 40, 24, cam);
            drawDeco(g, SmSprites.cloud, bx + 270, 40, cam);
            drawDeco(g, SmSprites.hill, bx + 384, 13 * 16 - 8, cam);
            drawDeco(g, SmSprites.bush, bx + 560, 13 * 16 - 6, cam);
            drawDeco(g, SmSprites.cloud, bx + 520, 20, cam);
            drawDeco(g, SmSprites.cloud, bx + 690, 52, cam);
            rep = rep + 1;
        }
        if (level != null) {
            drawDeco(g, SmSprites.castle, level.endX - 36, 13 * 16 - 12, cam);
        }
    }

    void drawDeco(Graphics g, Image img, int wx, int wy, int cam) {
        int S = SmSprites.SCALE;
        int w = img.getWidth() / S;
        int sx = wx - cam;
        if (sx > SCREEN_W || sx + w < 0) {
            return;
        }
        g.drawImage(img, sx * S, wy * S);
    }

    void paintTiles(Graphics g) {
        int S = SmSprites.SCALE;
        int tx0 = camX / 16 - 1;
        int tx1 = (camX + SCREEN_W) / 16 + 1;
        int qf = (int) ((frame / 12) % 4);
        if (qf == 3) {
            qf = 1;
        }
        int ty = 0;
        while (ty < 15) {
            int tx = tx0;
            while (tx <= tx1) {
                char c = level.get(tx, ty);
                if (c != '.' && tx >= 0 && tx < level.cols) {
                    Image img = null;
                    if (c == '#') {
                        img = SmSprites.ground;
                        if (level.theme == 1 || (level.bonusStart > 0 && tx >= level.bonusStart)) {
                            img = SmSprites.groundU;
                        }
                    } else if (c == 'B') {
                        img = SmSprites.brick;
                        if (level.theme == 1 || (level.bonusStart > 0 && tx >= level.bonusStart)) {
                            img = SmSprites.brickU;
                        }
                    } else if (c == '?' || c == 'M' || c == 'S') {
                        img = SmSprites.question[qf];
                    } else if (c == 'u') {
                        img = SmSprites.usedBlock;
                    } else if (c == 'X') {
                        img = SmSprites.solid;
                        if (level.theme == 1 || (level.bonusStart > 0 && tx >= level.bonusStart)) {
                            img = SmSprites.solidU;
                        }
                    } else if (c == 'a') {
                        img = SmSprites.pipeTL;
                    } else if (c == 'b') {
                        img = SmSprites.pipeTR;
                    } else if (c == 'c') {
                        img = SmSprites.pipeBL;
                    } else if (c == 'd') {
                        img = SmSprites.pipeBR;
                    } else if (c == 'F') {
                        img = SmSprites.poleRod;
                    } else if (c == 'f') {
                        img = SmSprites.poleBall;
                    } else if (c == 'o') {
                        img = SmSprites.coin[(int) ((frame / 8) % 4)];
                    }
                    if (img != null) {
                        int off = 0;
                        int b = level.bump[ty * level.cols + tx];
                        if (b > 0) {
                            if (b > 4) {
                                off = (8 - b) * 2;
                            } else {
                                off = b * 2;
                            }
                        }
                        g.drawImage(img, (tx * 16 - camX) * S, (ty * 16 - off) * S);
                    }
                }
                tx = tx + 1;
            }
            ty = ty + 1;
        }
        // flag cloth rides down the pole
        int fy = 3 * 16;
        if (player != null && player.state >= 4) {
            fy = (int) Math.min(11 * 16 - 8, Math.max(3 * 16, player.y + player.h - 8 - 16));
            if (player.state == 4) {
                fy = (int) Math.min(11 * 16 - 8, Math.max(3 * 16, player.y + 8));
            } else {
                fy = 11 * 16 - 8;
            }
        }
        g.drawImage(SmSprites.flagCloth, (level.flagX * 16 - 8 - camX) * S, fy * S);
    }

    void paintItemsBehind(Graphics g) {
        int S = SmSprites.SCALE;
        int i = 0;
        while (i < items.size()) {
            SmItem it = items.get(i);
            if (it.kind == 0 && it.emerge > 0) {
                g.drawImage(SmSprites.mushroom, (int) (it.x - 2 - camX) * S, (int) it.y * S);
            } else if (it.kind == 6 && it.emerge > 0) {
                g.drawImage(SmSprites.starItem, (int) (it.x - 2 - camX) * S, (int) it.y * S);
            } else if (it.kind == 4 && it.emerge > 0) {
                g.drawImage(SmSprites.flower[(it.frame / 6) % 2], (int) (it.x - 2 - camX) * S, (int) it.y * S);
            }
            i = i + 1;
        }
    }

    void paintEntities(Graphics g) {
        int S = SmSprites.SCALE;
        int i = 0;
        while (i < items.size()) {
            SmItem it = items.get(i);
            if (it.kind == 0 && it.emerge == 0) {
                g.drawImage(SmSprites.mushroom, (int) (it.x - 2 - camX) * S, (int) it.y * S - 2 * S);
            } else if (it.kind == 1) {
                g.drawImage(SmSprites.coin[(int) ((frame / 3) % 4)], (int) (it.x - 2 - camX) * S, (int) it.y * S);
            } else if (it.kind == 2) {
                drawText(g, String.valueOf(it.value), (int) (it.x - camX) * S, (int) it.y * S, smallFont);
            } else if (it.kind == 3) {
                g.drawImage(SmSprites.debris, (int) (it.x - camX) * S, (int) it.y * S);
            } else if (it.kind == 4 && it.emerge == 0) {
                g.drawImage(SmSprites.flower[(it.frame / 6) % 2], (int) (it.x - 2 - camX) * S, (int) it.y * S - 2 * S);
            } else if (it.kind == 6 && it.emerge == 0) {
                g.drawImage(SmSprites.starItem, (int) (it.x - 2 - camX) * S, (int) it.y * S - 2 * S);
            } else if (it.kind == 7) {
                Image dust = SmSprites.dust[0];
                if (it.life < 7) {
                    dust = SmSprites.dust[1];
                }
                g.drawImage(dust, (int) (it.x - camX) * S, (int) it.y * S);
            } else if (it.kind == 5) {
                g.drawImage(SmSprites.spark, (int) (it.x - camX) * S, (int) it.y * S);
            }
            i = i + 1;
        }
        i = 0;
        while (i < fireballs.size()) {
            SmFireball f = fireballs.get(i);
            g.drawImage(SmSprites.fireball[(f.anim / 3) % 4], (int) (f.x - camX) * S, (int) f.y * S);
            i = i + 1;
        }
        i = 0;
        while (i < enemies.size()) {
            SmEnemy e = enemies.get(i);
            if (e.active != 0) {
                paintEnemy(g, e);
            }
            i = i + 1;
        }
        if (player.state != 7 && player.state != 8) {
            paintPlayer(g);
        }
    }

    void paintEnemy(Graphics g, SmEnemy e) {
        int S = SmSprites.SCALE;
        int sx = (int) (e.x - 1 - camX) * S;
        int sy = (int) (e.y - 2) * S;
        if (e.kind == 0) {
            if (e.squash > 0) {
                g.drawImage(SmSprites.goombaFlat, sx, sy);
            } else {
                Image img = SmSprites.goomba[(e.anim / 10) % 2];
                if (e.dead != 0) {
                    g.drawImage(SmSprites.goombaDead, sx, sy);
                } else {
                    g.drawImage(img, sx, sy);
                }
            }
        } else if (e.kind == 1) {
            Image img = SmSprites.koopaL[(e.anim / 10) % 2];
            if (e.dir > 0) {
                img = SmSprites.koopaR[(e.anim / 10) % 2];
            }
            if (e.dead != 0) {
                img = SmSprites.koopaDead;
            }
            g.drawImage(img, sx, sy);
        } else {
            g.drawImage(SmSprites.shell, sx, sy);
        }
    }

    void paintPlayer(Graphics g) {
        int S = SmSprites.SCALE;
        if (player.state == 6) {
            return;
        }
        if (player.invincible > 0 && (player.invincible / 3) % 2 == 0 && player.state == 0) {
            return;
        }
        int frameIdx = 0;
        int big = player.big;
        if (player.state == 1) {
            // growing: flicker between small and big
            if ((freeze / 4) % 2 == 0) {
                big = 0;
            } else {
                big = 1;
            }
        }
        if (player.state == 3) {
            frameIdx = 5;
        } else if (player.state == 4) {
            frameIdx = 3;
            if (player.y + player.h >= 12 * 16 - 1) {
                frameIdx = 0;
            }
        } else if (player.crouching != 0) {
            frameIdx = 5;
        } else if (player.onGround == 0 && player.state != 5) {
            frameIdx = 3;
        } else if (player.skidding != 0) {
            frameIdx = 4;
        } else if (Math.abs(player.vx) > 0.05) {
            frameIdx = 1 + (player.walkFrame % 2);
        }
        Image img;
        int right = 1;
        if (player.facing < 0) {
            right = 0;
        }
        if (player.skidding != 0) {
            right = 1 - right;
        }
        if (big != 0) {
            if (player.fire != 0) {
                if (right != 0) {
                    img = SmSprites.bigFireR[frameIdx];
                } else {
                    img = SmSprites.bigFireL[frameIdx];
                }
            } else if (right != 0) {
                img = SmSprites.bigR[frameIdx];
            } else {
                img = SmSprites.bigL[frameIdx];
            }
        } else {
            if (frameIdx == 5 && player.state != 3) {
                frameIdx = 0;
            }
            if (right != 0) {
                img = SmSprites.heroR[frameIdx];
            } else {
                img = SmSprites.heroL[frameIdx];
            }
        }
        if (player.star > 0 && player.state == 0) {
            int variant = (int) ((frame / 3) % 3);
            if (variant > 0) {
                int vi = variant - 1;
                if (big != 0) {
                    if (right != 0) {
                        img = SmSprites.starBigR[vi][frameIdx];
                    } else {
                        img = SmSprites.starBigL[vi][frameIdx];
                    }
                } else {
                    if (frameIdx == 5) {
                        frameIdx = 0;
                    }
                    if (right != 0) {
                        img = SmSprites.starSmallR[vi][frameIdx];
                    } else {
                        img = SmSprites.starSmallL[vi][frameIdx];
                    }
                }
            }
        }
        int spriteH = 16;
        if (big != 0) {
            spriteH = 32;
        }
        int sx = (int) (player.x - 2 - camX) * S;
        int sy = (int) (player.y + player.h - spriteH) * S;
        g.drawImage(img, sx, sy);
    }

    void paintHud(Graphics g) {
        int S = SmSprites.SCALE;
        drawText(g, "SCORE", 16 * S, 14 * S, hudFont);
        drawText(g, pad(score, 6), 16 * S, 30 * S, hudFont);
        drawText(g, "COINS", 88 * S, 14 * S, hudFont);
        drawText(g, "x" + pad(coins, 2), 98 * S, 30 * S, hudFont);
        drawText(g, "WORLD", 148 * S, 14 * S, hudFont);
        drawText(g, worldNo + "-" + stageNo, 160 * S, 30 * S, hudFont);
        drawText(g, "TIME", 208 * S, 14 * S, hudFont);
        drawText(g, pad(time, 3), 214 * S, 30 * S, hudFont);
        drawText(g, "LIVES " + lives, 8 * S, 232 * S, smallFont);
    }
}
