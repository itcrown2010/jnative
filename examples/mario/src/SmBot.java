/** Scripted input for headless verification runs. */
public class SmBot {
    static void drive(SmGame game, int f) {
        game.setKey(13, 0);
        game.setKey(39, 1);
        game.setKey(88, 1);
        if (game.player.fire != 0 && f % 24 >= 12) {
            game.setKey(88, 0);
        }
        // hop regularly; hold jump for the first part of each hop
        int phase = f % 70;
        if (phase < 28) {
            game.setKey(90, 1);
        } else {
            game.setKey(90, 0);
        }
    }
}
