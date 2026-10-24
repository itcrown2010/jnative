import jnative.collections.*;

/**
 * A recorded match: seed, map size, every command with its tick and periodic checksums. Playing the
 * commands back into a fresh simulation reproduces the match exactly; the checksums prove it.
 *
 * @author 张晓达
 */
public class ScReplay {
    long seed;
    int size;
    int players;
    ArrayList<ScCommand> cmds = new ArrayList<ScCommand>();
    ArrayList<Long> cpTick = new ArrayList<Long>();
    ArrayList<Long> cpSum = new ArrayList<Long>();
    long length;
    int winner = -1;
    int level = 1;
    long stamp;
    String[] names = new String[8];
    int feedPos;

    ScReplay(long s, int sz, int p) {
        seed = s;
        size = sz;
        players = p;
    }

    void add(ScCommand c) {
        cmds.add(c);
    }

    void checkpoint(long tick, long sum) {
        cpTick.add(tick);
        cpSum.add(sum);
        length = tick;
    }

    ScSim start() {
        feedPos = 0;
        return new ScSim(seed, size, players);
    }

    /** Queue every command recorded for the simulation's current tick. */
    void feed(ScSim sim) {
        while (feedPos < cmds.size() && cmds.get(feedPos).tick <= sim.tick) {
            sim.queueCommand(cmds.get(feedPos));
            feedPos = feedPos + 1;
        }
    }

    /** Re-simulate from scratch and compare every checkpoint. Returns 1 when all match. */
    int verify(long ticks) {
        ScSim sim = start();
        int next = 0;
        while (sim.tick < ticks) {
            feed(sim);
            sim.step();
            while (next < cpTick.size() && cpTick.get(next) <= sim.tick) {
                if (cpTick.get(next) == sim.tick && cpSum.get(next) != sim.checksum()) {
                    return 0;
                }
                next = next + 1;
            }
        }
        return 1;
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("JNSCREPLAY 1\n");
        sb.append("seed ").append(seed).append("\n");
        sb.append("size ").append(size).append("\n");
        sb.append("players ").append(players).append("\n");
        sb.append("length ").append(length).append("\n");
        sb.append("winner ").append(winner).append("\n");
        sb.append("level ").append(level).append("\n");
        sb.append("stamp ").append(stamp).append("\n");
        int i = 0;
        while (i < cpTick.size()) {
            sb.append("cp ").append(cpTick.get(i)).append(" ").append(cpSum.get(i)).append("\n");
            i = i + 1;
        }
        i = 0;
        while (i < cmds.size()) {
            sb.append("c ").append(cmds.get(i).encode()).append("\n");
            i = i + 1;
        }
        return sb.toString();
    }

    static ScReplay parse(String text) {
        String[] lines = text.split("\n");
        long seed = 0;
        int size = 64;
        int players = 2;
        long length = 0;
        int winner = -1;
        int level = 1;
        long stamp = 0;
        ArrayList<ScCommand> cmds = new ArrayList<ScCommand>();
        ArrayList<Long> ct = new ArrayList<Long>();
        ArrayList<Long> cs = new ArrayList<Long>();
        int i = 1;
        while (i < lines.length) {
            String ln = lines[i];
            i = i + 1;
            if (ln.startsWith("seed ")) {
                seed = Long.parseLong(ln.substring(5));
            } else if (ln.startsWith("size ")) {
                size = Integer.parseInt(ln.substring(5));
            } else if (ln.startsWith("players ")) {
                players = Integer.parseInt(ln.substring(8));
            } else if (ln.startsWith("length ")) {
                length = Long.parseLong(ln.substring(7));
            } else if (ln.startsWith("winner ")) {
                winner = Integer.parseInt(ln.substring(7));
            } else if (ln.startsWith("level ")) {
                level = Integer.parseInt(ln.substring(6));
            } else if (ln.startsWith("stamp ")) {
                stamp = Long.parseLong(ln.substring(6));
            } else if (ln.startsWith("cp ")) {
                String[] f = ln.split(" ");
                ct.add(Long.parseLong(f[1]));
                cs.add(Long.parseLong(f[2]));
            } else if (ln.startsWith("c ")) {
                cmds.add(ScCommand.decode(ln.substring(2)));
            }
        }
        ScReplay r = new ScReplay(seed, size, players);
        r.cmds = cmds;
        r.cpTick = ct;
        r.cpSum = cs;
        r.length = length;
        r.winner = winner;
        r.level = level;
        r.stamp = stamp;
        return r;
    }
}
