import jnative.collections.*;

/**
 * Synthesized sound effects and an ambient loop, written to WAV files at start-up and played with
 * macOS {@code afplay}. Silent (and harmless) when disabled.
 *
 * @author 张晓达
 */
public class ScAudio {
    static int RATE = 22050;
    static String dir = "/tmp/jnative-colonists-audio";
    static int enabled = 0;
    static int COUNT = 10;
    static String[] paths = new String[10];
    static long[] lastPlay = new long[10];
    static String musicPath = "";
    static double musicSeconds = 0;
    static Process musicProc = null;
    static long musicStartMs = 0;
    static int musicOn = 0;
    static ArrayList<Process> running = new ArrayList<Process>();

    static int SHOT = 0;
    static int BOOM = 1;
    static int BUILT = 2;
    static int READY = 3;
    static int ALERT = 4;
    static int ACK = 5;
    static int ERROR = 6;
    static int WIN = 7;
    static int LOSE = 8;
    static int SELECT = 9;

    static double freq(int semis) {
        return 261.6256 * Math.pow(2.0, semis / 12.0);
    }

    /** wave: 0 square, 1 thin square, 2 triangle, 3 noise, 4 sine. */
    static void tone(double[] buf, double startSec, double lenSec, double f0, double f1, double vol, int wave) {
        int start = (int) (startSec * RATE);
        int n = (int) (lenSec * RATE);
        double phase = 0.0;
        int seed = 777 + start;
        int i = 0;
        while (i < n) {
            int idx = start + i;
            if (idx >= buf.length) {
                return;
            }
            double t = (double) i / n;
            double f = f0 + (f1 - f0) * t;
            phase = phase + f / RATE;
            phase = phase - Math.floor(phase);
            double v = 0.0;
            if (wave == 0) {
                v = phase < 0.5 ? 1.0 : -1.0;
            } else if (wave == 1) {
                v = phase < 0.25 ? 1.0 : -1.0;
            } else if (wave == 2) {
                v = phase < 0.5 ? phase * 4.0 - 1.0 : 3.0 - phase * 4.0;
            } else if (wave == 3) {
                seed = seed * 1103515245 + 12345;
                v = ((seed >>> 16) % 2048) / 1024.0 - 1.0;
            } else {
                v = Math.sin(phase * 6.283185307);
            }
            double env = 1.0;
            if (i < 90) {
                env = i / 90.0;
            }
            double tail = 1.0 - t;
            if (tail < 0.5) {
                env = env * (tail / 0.5);
            }
            buf[idx] = buf[idx] + v * vol * env;
            i = i + 1;
        }
    }

    static byte[] toWav(double[] buf) {
        int n = buf.length;
        int dataLen = n * 2;
        byte[] out = new byte[44 + dataLen];
        putStr(out, 0, "RIFF");
        putInt(out, 4, 36 + dataLen);
        putStr(out, 8, "WAVE");
        putStr(out, 12, "fmt ");
        putInt(out, 16, 16);
        out[20] = 1;
        out[22] = 1;
        putInt(out, 24, RATE);
        putInt(out, 28, RATE * 2);
        out[32] = 2;
        out[34] = 16;
        putStr(out, 36, "data");
        putInt(out, 40, dataLen);
        int i = 0;
        while (i < n) {
            double v = buf[i];
            if (v > 1.0) {
                v = 1.0;
            }
            if (v < -1.0) {
                v = -1.0;
            }
            int s = (int) (v * 30000.0);
            out[44 + i * 2] = (byte) (s & 255);
            out[45 + i * 2] = (byte) ((s >> 8) & 255);
            i = i + 1;
        }
        return out;
    }

    static void putStr(byte[] b, int off, String s) {
        int i = 0;
        while (i < s.length()) {
            b[off + i] = (byte) s.charAt(i);
            i = i + 1;
        }
    }

    static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 255);
        b[off + 1] = (byte) ((v >> 8) & 255);
        b[off + 2] = (byte) ((v >> 16) & 255);
        b[off + 3] = (byte) ((v >> 24) & 255);
    }

    static String save(String name, double[] buf) {
        String path = dir + "/" + name + ".wav";
        File.writeBytes(path, toWav(buf));
        return path;
    }

    static double[] buf(double seconds) {
        return new double[(int) (seconds * RATE)];
    }

    static void buildEffects() {
        double[] b = buf(0.16);
        tone(b, 0.0, 0.14, 1500, 300, 0.45, 3);
        tone(b, 0.0, 0.12, 900, 180, 0.35, 1);
        paths[SHOT] = save("shot", b);
        b = buf(0.8);
        tone(b, 0.0, 0.75, 220, 40, 0.6, 3);
        tone(b, 0.0, 0.6, 90, 30, 0.7, 2);
        paths[BOOM] = save("boom", b);
        b = buf(0.7);
        tone(b, 0.0, 0.18, freq(0), freq(0), 0.3, 2);
        tone(b, 0.15, 0.18, freq(4), freq(4), 0.3, 2);
        tone(b, 0.30, 0.38, freq(7), freq(7), 0.3, 2);
        tone(b, 0.30, 0.38, freq(12), freq(12), 0.18, 4);
        paths[BUILT] = save("built", b);
        b = buf(0.4);
        tone(b, 0.0, 0.12, freq(12), freq(12), 0.3, 4);
        tone(b, 0.1, 0.28, freq(19), freq(19), 0.3, 4);
        paths[READY] = save("ready", b);
        b = buf(0.8);
        int k = 0;
        while (k < 3) {
            tone(b, k * 0.25, 0.13, 880, 880, 0.3, 0);
            tone(b, k * 0.25 + 0.12, 0.11, 660, 660, 0.3, 0);
            k = k + 1;
        }
        paths[ALERT] = save("alert", b);
        b = buf(0.08);
        tone(b, 0.0, 0.07, 1200, 1500, 0.22, 4);
        paths[ACK] = save("ack", b);
        b = buf(0.25);
        tone(b, 0.0, 0.22, 140, 100, 0.4, 0);
        paths[ERROR] = save("error", b);
        b = buf(2.2);
        int[] melody = new int[] { 0, 4, 7, 12, 7, 12, 16 };
        k = 0;
        while (k < melody.length) {
            tone(b, k * 0.22, 0.32, freq(melody[k]), freq(melody[k]), 0.28, 2);
            tone(b, k * 0.22, 0.32, freq(melody[k] + 12), freq(melody[k] + 12), 0.12, 4);
            k = k + 1;
        }
        paths[WIN] = save("win", b);
        b = buf(2.0);
        int[] sad = new int[] { 7, 3, 0, -5 };
        k = 0;
        while (k < sad.length) {
            tone(b, k * 0.42, 0.5, freq(sad[k]), freq(sad[k]) * 0.98, 0.3, 2);
            k = k + 1;
        }
        paths[LOSE] = save("lose", b);
        b = buf(0.06);
        tone(b, 0.0, 0.05, 700, 900, 0.18, 4);
        paths[SELECT] = save("select", b);
    }

    /** 32 s ambient loop: Am - F - C - G pads, a slow arpeggio and a low pulse. */
    static void buildMusic() {
        double bar = 8.0;
        double[] b = buf(bar * 4);
        int[][] chords = new int[][] { { -3, 0, 4 }, { -7, -3, 0 }, { 0, 4, 7 }, { -5, -1, 2 } };
        int[] roots = new int[] { -24, -28, -24, -29 };
        int c = 0;
        while (c < 4) {
            double t0 = c * bar;
            int n = 0;
            while (n < 3) {
                tone(b, t0, bar + 0.4, freq(chords[c][n]), freq(chords[c][n]), 0.07, 2);
                n = n + 1;
            }
            tone(b, t0, bar, freq(roots[c]), freq(roots[c]), 0.16, 4);
            int s = 0;
            while (s < 16) {
                int note = chords[c][s % 3] + 12 + (s % 4 == 3 ? 12 : 0);
                tone(b, t0 + s * 0.5 + 0.02, 0.9, freq(note), freq(note), 0.045, 4);
                s = s + 1;
            }
            int p = 0;
            while (p < 8) {
                tone(b, t0 + p * 1.0, 0.18, freq(roots[c]) * 1.0, freq(roots[c]) * 0.9, 0.09, 2);
                p = p + 1;
            }
            c = c + 1;
        }
        musicSeconds = bar * 4;
        musicPath = save("music", b);
    }

    static void init() {
        Process mk = new ProcessBuilder("/bin/mkdir").argument("-p").argument(dir).start();
        if (mk == null) {
            return;
        }
        mk.waitFor();
        buildEffects();
        buildMusic();
        enabled = 1;
    }

    static void reap() {
        int i = 0;
        while (i < running.size()) {
            Process p = running.get(i);
            if (p.isAlive()) {
                i = i + 1;
            } else {
                running.remove(i);
            }
        }
    }

    /** Play an effect; minGapMs throttles repeats of the same sound. */
    static void play(int id, int minGapMs) {
        if (enabled == 0 || id < 0 || id >= COUNT) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlay[id] < minGapMs) {
            return;
        }
        reap();
        if (running.size() > 10) {
            return;
        }
        lastPlay[id] = now;
        Process p = new ProcessBuilder("/usr/bin/afplay").argument("-v").argument("0.55").argument(paths[id]).start();
        if (p != null) {
            running.add(p);
        }
    }

    static void musicStart() {
        if (enabled == 0 || musicOn != 0) {
            return;
        }
        musicOn = 1;
        spawnMusic();
    }

    static void spawnMusic() {
        musicProc = new ProcessBuilder("/usr/bin/afplay").argument("-v").argument("0.30").argument(musicPath).start();
        musicStartMs = System.currentTimeMillis();
    }

    static void musicStop() {
        musicOn = 0;
        if (musicProc != null) {
            musicProc.destroy();
            musicProc = null;
        }
    }

    static void musicTick() {
        if (enabled == 0 || musicOn == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - musicStartMs > (long) (musicSeconds * 1000.0) - 60) {
            Process old = musicProc;
            spawnMusic();
            if (old != null) {
                running.add(old);
            }
        }
    }
}
