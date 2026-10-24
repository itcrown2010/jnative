import jnative.graphics.*;

/**
 * Renders reference screenshots without a window ({@code --cards <prefix>}) so the visuals can be
 * checked from the command line.
 *
 * @author 张晓达
 */
public class ScCards {
    static void shot(ScGame game, Image img, String path) {
        Graphics g = img.beginGraphics();
        game.view.paint(g);
        g.end();
        img.save(path);
    }

    static void run(String prefix) {
        ScGame game = new ScGame();
        Image img = Image.create(ScGame.W, ScGame.H);
        game.frame = 40;
        shot(game, img, prefix + ".menu.png");
        game.newGame();
        // a little play: AI for me too for a while to get stuff on screen
        ScAI mine = new ScAI(game.sim, 0);
        int t = 0;
        while (t < 24 * 100) {
            mine.think();
            game.stepLive();
            t = t + 1;
        }
        game.frame = 100;
        game.view.updateGhosts();
        game.view.updateMinimapFog();
        ScEntity hub = game.sim.nearestOwned(0, ScDefs.HUB, 0, 0, 0);
        game.centerOn(hub.x, hub.y);
        shot(game, img, prefix + ".game.png");
        long t0 = System.nanoTime();
        int k = 0;
        while (k < 60) {
            game.frame = game.frame + 1;
            Graphics bg = img.beginGraphics();
            game.view.paint(bg);
            bg.end();
            k = k + 1;
        }
        System.out.println("paint avg ms = " + (System.nanoTime() - t0) / 60 / 1000 / 1000.0);
        System.out.println("terrain " + game.view.prof[0] / 60000 / 1000.0 + " water " + game.view.prof[1] / 60000 / 1000.0 + " ents " + game.view.prof[2] / 60000 / 1000.0 + " fog " + game.view.prof[3] / 60000 / 1000.0 + " over " + game.view.prof[4] / 60000 / 1000.0 + " hud " + game.view.prof[5] / 60000 / 1000.0);
        // select some workers
        game.boxSelect(hub.x - 200, hub.y, hub.x + 200, hub.y + 300, 0);
        shot(game, img, prefix + ".select.png");
        game.selectOnly(hub);
        shot(game, img, prefix + ".hub.png");
        // a battle, seen in a replay with full vision
        ScGame bg = new ScGame();
        bg.newGame();
        bg.ai[0] = new ScAI(bg.sim, 0);
        int tt = 0;
        while (tt < 24 * 336) {
            bg.stepLive();
            tt = tt + 1;
        }
        bg.rec.length = bg.sim.tick;
        bg.rec.checkpoint(bg.sim.tick, bg.sim.checksum());
        ScReplay rp = ScReplay.parse(bg.rec.serialize());
        ScGame rg = new ScGame();
        rg.startReplay(rp);
        rg.seekReplay(24 * 336);
        int guard = 0;
        while (rg.seekTarget >= 0 && guard < 100000) {
            rg.update(System.nanoTime());
            guard = guard + 1;
        }
        rg.viewPlayer = -1;
        rg.frame = 200;
        rg.centerOn(rg.sim.lastAttackX, rg.sim.lastAttackY);
        shot(rg, img, prefix + ".battle.png");
        rg.viewPlayer = 0;
        rg.view.updateMinimapFog();
        shot(rg, img, prefix + ".replay.png");
    }
}
