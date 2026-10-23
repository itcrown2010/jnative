import jnative.ui.*;
import jnative.graphics.*;

/**
 * Window, input and the fixed-step main loop.
 *
 * @author 张晓达
 */
public class SmMain {
    static SmGame game;
    static Canvas canvas;
    static long lastNs;
    static long accNs;

    public static void main(String[] args) {
        SmSprites.init();
        game = new SmGame();
        if (args.length >= 1 && args[0].equals("--selftest")) {
            SmSelfTest.run();
            return;
        }
        if (args.length < 1 || !args[0].equals("--headless") && !args[0].equals("--cards")) {
            SmAudio.init();
        }
        if (args.length >= 2 && args[0].equals("--cards")) {
            int cs = SmSprites.SCALE;
            Image card = Image.create(SmGame.SCREEN_W * cs, SmGame.SCREEN_H * cs);
            game.frame = 30;
            Graphics tg = card.beginGraphics();
            game.paint(tg);
            tg.end();
            card.save(args[1] + ".title.png");
            game.newGame();
            tg = card.beginGraphics();
            game.paint(tg);
            tg.end();
            card.save(args[1] + ".intro.png");
            return;
        }
        if (args.length >= 3 && args[0].equals("--headless")) {
            headless(args);
            return;
        }
        int S = SmSprites.SCALE;
        Application app = new Application();
        Window window = new Window("JNative Runner", SmGame.SCREEN_W * S, SmGame.SCREEN_H * S);
        canvas = new Canvas();
        canvas.onKeyDown(e -> {
            game.onKey(e.keyCode, e.rawMods, 1);
        });
        canvas.onKeyUp(e -> {
            game.onKey(e.keyCode, e.rawMods, 0);
        });
        canvas.onPaint(g -> {
            game.paint(g);
        });
        UiTimer timer = new UiTimer(8, () -> {
            tick();
        });
        window.setContent(canvas);
        window.show();
        canvas.requestFocus();
        app.run();
    }

    /**
     * Test mode without a window: {@code --headless <frames> <out.png> [shotEvery]} plays the
     * level with a scripted bot and writes screenshots (used by the verification scripts).
     */
    static void headless(String[] args) {
        int frames = Integer.parseInt(args[1]);
        String out = args[2];
        int every = 0;
        if (args.length >= 4) {
            every = Integer.parseInt(args[3]);
        }
        int S = SmSprites.SCALE;
        Image shot = Image.create(SmGame.SCREEN_W * S, SmGame.SCREEN_H * S);
        game.introLen = 0;
        game.setKey(13, 1);
        game.update();
        if (args.length >= 7) {
            game.levelIndex = Integer.parseInt(args[6]);
            game.loadLevel();
        }
        if (args.length >= 5) {
            int sx = Integer.parseInt(args[4]);
            game.player.x = sx * 16 + 2;
            game.player.y = 12 * 16;
            game.camX = sx * 16 - 112;
            if (sx >= 220) {
                game.camX = 220 * 16;
            }
            if (game.camX < 0) {
                game.camX = 0;
            }
        }
        if (args.length >= 6 && args[5].equals("1")) {
            game.player.setBig(1);
        }
        if (args.length >= 8 && args[7].equals("1")) {
            game.player.setBig(1);
            game.player.fire = 1;
        }
        long t0 = System.nanoTime();
        if (every < 0) {
            int k = 0;
            while (k < 300) {
                Graphics pg = shot.beginGraphics();
                game.paint(pg);
                pg.end();
                k = k + 1;
            }
            long dt = System.nanoTime() - t0;
            System.out.println("paint avg ms = " + (dt / 300 / 1000) / 1000.0);
            return;
        }
        int f = 0;
        while (f < frames) {
            SmBot.drive(game, f);
            game.update();
            if (every > 0 && f % every == 0 && f > 0) {
                Graphics g = shot.beginGraphics();
                game.paint(g);
                g.end();
                shot.save(out + "." + f + ".png");
            }
            f = f + 1;
        }
        Graphics g = shot.beginGraphics();
        game.paint(g);
        g.end();
        shot.save(out);
        System.out.println("frames=" + frames + " state=" + game.state + " px=" + game.player.x
                + " py=" + game.player.y + " score=" + game.score + " coins=" + game.coins
                + " lives=" + game.lives + " big=" + game.player.big + " time=" + game.time);
    }

    static void tick() {
        long now = System.nanoTime();
        if (lastNs == 0) {
            lastNs = now;
        }
        accNs = accNs + (now - lastNs);
        lastNs = now;
        if (accNs > 100000000L) {
            accNs = 100000000L;
        }
        int steps = 0;
        SmAudio.musicTick();
        while (accNs >= 16666667L) {
            game.update();
            accNs = accNs - 16666667L;
            steps = steps + 1;
        }
        if (steps > 0) {
            canvas.repaint();
        }
    }
}
