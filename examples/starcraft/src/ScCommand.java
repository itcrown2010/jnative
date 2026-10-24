/**
 * A player order. Every change a human or the AI makes to the world goes through one of these,
 * which is what makes recording and replaying a match possible.
 *
 * @author 张晓达
 */
public class ScCommand {
    static int MOVE = 1;
    static int ATTACKMOVE = 2;
    static int ATTACK = 3;
    static int STOP = 4;
    static int HOLD = 5;
    static int GATHER = 6;
    static int BUILD = 7;
    static int TRAIN = 8;
    static int CANCEL = 9;
    static int RALLY = 10;
    static int RETURN = 11;
    static int SURRENDER = 12;

    long tick;
    int player;
    int type;
    int x;
    int y;
    int target = -1;
    int arg;
    int[] ids = new int[0];

    ScCommand(long t, int p, int ty) {
        tick = t;
        player = p;
        type = ty;
    }

    String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(tick).append(" ").append(player).append(" ").append(type).append(" ");
        sb.append(x).append(" ").append(y).append(" ").append(target).append(" ").append(arg).append(" ");
        sb.append(ids.length);
        int i = 0;
        while (i < ids.length) {
            sb.append(" ").append(ids[i]);
            i = i + 1;
        }
        return sb.toString();
    }

    static ScCommand decode(String line) {
        String[] f = line.split(" ");
        ScCommand c = new ScCommand(Long.parseLong(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
        c.x = Integer.parseInt(f[3]);
        c.y = Integer.parseInt(f[4]);
        c.target = Integer.parseInt(f[5]);
        c.arg = Integer.parseInt(f[6]);
        int n = Integer.parseInt(f[7]);
        c.ids = new int[n];
        int i = 0;
        while (i < n) {
            c.ids[i] = Integer.parseInt(f[8 + i]);
            i = i + 1;
        }
        return c;
    }
}
