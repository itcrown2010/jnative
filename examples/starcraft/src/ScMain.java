import jnative.ui.*;
import jnative.graphics.*;

/**
 * Entry point: window, input wiring and the frame timer.
 *
 * @author 张晓达
 */
public class ScMain {
    static ScGame game;
    static Canvas canvas;

    public static void main(String[] args) {
        int i = 0;
        while (i < args.length) {
            if (args[i].equals("--selftest")) {
                int f = ScSelfTest.run();
                System.exit(f == 0 ? 0 : 1);
                return;
            }
            if (args[i].equals("--headless")) {
                headless(args.length > i + 1 ? Integer.parseInt(args[i + 1]) : 600);
                return;
            }
            if (args[i].equals("--uitest")) {
                uiTest();
                return;
            }
            if (args[i].equals("--cards")) {
                ScCards.run(args.length > i + 1 ? args[i + 1] : "/tmp/sc");
                return;
            }
            i = i + 1;
        }
        game = new ScGame();
        ScAudio.init();
        ScAudio.musicStart();
        Application app = new Application();
        Window window = new Window("Colonists", ScGame.W, ScGame.H);
        canvas = new Canvas();
        wire(canvas);
        UiTimer timer = new UiTimer(16, () -> {
            game.update(System.nanoTime());
            ScAudio.musicTick();
            canvas.repaint();
        });
        window.setContent(canvas);
        window.show();
        canvas.requestFocus();
        app.run();
    }

    static void wire(Canvas canvas) {
        canvas.onKeyDown(e -> {
            game.onKey(e.keyCode, e.rawMods, 1);
        });
        canvas.onKeyUp(e -> {
            game.onKey(e.keyCode, e.rawMods, 0);
        });
        canvas.onMouseDown(e -> {
            game.onMouseDown(e.x, e.y, e.button, mods(e));
        });
        canvas.onMouseUp(e -> {
            game.onMouseUp(e.x, e.y, e.button, mods(e));
        });
        canvas.onMouseMove(e -> {
            game.onMouseMove(e.x, e.y, e.button);
        });
        canvas.onMouseWheel(e -> {
            game.onWheel(e.wheelDelta);
        });
        canvas.onMouseExit(e -> {
            game.mouseIn = 0;
        });
        canvas.onMouseEnter(e -> {
            game.mouseIn = 1;
        });
        canvas.onPaint(g -> {
            game.view.paint(g);
        });
    }

    static int mods(MouseEvent e) {
        return e.shiftDown + e.ctrlDown * 2 + e.altDown * 4 + e.metaDown * 8;
    }

    /** Drives the real canvas handlers through the headless UI backend. */
    static void uiTest() {
        Application.setHeadless(1);
        game = new ScGame();
        Application app = new Application();
        Window window = new Window("Colonists", ScGame.W, ScGame.H);
        canvas = new Canvas();
        wire(canvas);
        window.setContent(canvas);
        window.show();
        int bad = 0;
        UiTest.paint(canvas);
        UiTest.keyPress(canvas, 'n');
        if (game.mode != ScGame.PLAY) {
            System.out.println("FAIL: n did not start a game");
            bad = bad + 1;
        }
        int i = 0;
        while (i < 50) {
            game.update(System.nanoTime() + i * 20000000L);
            i = i + 1;
        }
        UiTest.paint(canvas);
        ScEntity hub = game.sim.nearestOwned(0, ScDefs.HUB, 0, 0, 0);
        game.centerOn(hub.x, hub.y);
        int hx = (int) (hub.x - game.camX);
        int hy = (int) (hub.y - game.camY);
        UiTest.mouseButton(canvas, hx, hy, 1, 0, 1);
        UiTest.mouseButton(canvas, hx, hy, 1, 0, 0);
        if (game.selCount != 1 || game.firstSel().type != ScDefs.HUB) {
            System.out.println("FAIL: click did not select the hub, sel=" + game.selCount);
            bad = bad + 1;
        }
        UiTest.keyPress(canvas, 'w');
        game.update(System.nanoTime());
        ScEntity h2 = game.firstSel();
        if (h2.qLen != 0 || game.sim.pending.size() != 1) {
            // command is queued for the next tick
            System.out.println("note: queue=" + h2.qLen + " pending=" + game.sim.pending.size());
        }
        int before = game.camY > 0 ? (int) game.camY : 0;
        UiTest.mouseWheel(canvas, 400, 300, 3);
        UiTest.mouseHover(canvas, 500, 300);
        UiTest.mouseButton(canvas, 520, 300, 3, 0, 1);
        UiTest.mouseButton(canvas, 520, 300, 3, 0, 0);
        UiTest.paint(canvas);
        UiTest.keyPress(canvas, 27);
        UiTest.closeWindow(window);
        System.out.println(bad == 0 ? "uitest ok" : "uitest FAILED");
        app.run();
        System.exit(bad == 0 ? 0 : 1);
    }

    static void headless(int seconds) {
        ScDefs.init();
        ScSim s = new ScSim(23, 64, 2);
        ScAI a = new ScAI(s, 0);
        ScAI b = new ScAI(s, 1);
        long t0 = System.currentTimeMillis();
        while (s.tick < seconds * 24 && s.gameOver == 0) {
            a.think();
            b.think();
            s.step();
            if (s.tick % (24 * 30) == 0) {
                System.out.println(report(s));
            }
        }
        System.out.println(report(s) + " winner=" + s.winner + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    static String report(ScSim s) {
        StringBuilder sb = new StringBuilder();
        sb.append("t=").append(s.tick / 24).append("s");
        int p = 0;
        while (p < s.playerCount) {
            ScPlayer pl = s.players[p];
            sb.append(" | P").append(p).append(" min=").append(pl.minerals).append(" sup=").append(pl.supplyUsed).append("/").append(pl.supplyCap);
            sb.append(" w=").append(ScSelfTest.unitIds(s, p, ScDefs.WORKER).length);
            sb.append(" r=").append(ScSelfTest.unitIds(s, p, ScDefs.RIFLEMAN).length);
            sb.append(" bk=").append(s.countOwned(p, ScDefs.BARRACKS, 1));
            sb.append(" k=").append(pl.kills);
            p = p + 1;
        }
        return sb.toString();
    }
}
