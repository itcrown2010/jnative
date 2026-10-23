import jnative.collections.*;

/**
 * Chiptune sound: effects and an original looping tune are synthesized at start-up into
 * 16-bit WAV files and played through macOS {@code afplay}.
 *
 * @author 张晓达
 */
public class SmAudio {
    static int RATE = 22050;
    static String dir = "/tmp/jnative-runner-audio";
    static int enabled = 0;
    static int count = 12;
    static String[] paths = new String[12];
    static String musicPath = "";
    static double musicSeconds = 0;
    static Process musicProc = null;
    static long musicStartMs = 0;
    static int musicOn = 0;
    static int musicTrack = 0;
    static String[] musicPaths = new String[3];
    static double[] musicSecondsOf = new double[3];
    static ArrayList<Process> running = new ArrayList<Process>();

    // ------------------------------------------------------------------ synthesis

    static double freq(int semitoneFromC4) {
        return 261.6256 * Math.pow(2.0, semitoneFromC4 / 12.0);
    }

    /** Add a tone to buf. wave: 0 square 50%, 1 square 25%, 2 triangle, 3 noise. */
    static void tone(double[] buf, double startSec, double lenSec, double f0, double f1, double vol, int wave) {
        int start = (int) (startSec * RATE);
        int n = (int) (lenSec * RATE);
        double phase = 0.0;
        int seed = 12345;
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
                if (phase < 0.5) {
                    v = 1.0;
                } else {
                    v = -1.0;
                }
            } else if (wave == 1) {
                if (phase < 0.25) {
                    v = 1.0;
                } else {
                    v = -1.0;
                }
            } else if (wave == 2) {
                if (phase < 0.5) {
                    v = phase * 4.0 - 1.0;
                } else {
                    v = 3.0 - phase * 4.0;
                }
            } else {
                seed = seed * 1103515245 + 12345;
                v = ((seed >>> 16) % 2048) / 1024.0 - 1.0;
            }
            double env = 1.0;
            if (i < 110) {
                env = i / 110.0;
            }
            double tail = 1.0 - t;
            if (tail < 0.35) {
                env = env * (tail / 0.35);
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

    static double[] fx(double seconds) {
        return new double[(int) (seconds * RATE)];
    }

    static void buildEffects() {
        double[] b = fx(0.22);                                   // 0 jump
        tone(b, 0.0, 0.2, 330.0, 780.0, 0.35, 0);
        paths[0] = save("jump", b);

        b = fx(0.4);                                             // 1 coin
        tone(b, 0.0, 0.07, 988.0, 988.0, 0.3, 1);
        tone(b, 0.07, 0.32, 1319.0, 1319.0, 0.3, 1);
        paths[1] = save("coin", b);

        b = fx(0.6);                                             // 2 power-up appears
        int i = 0;
        while (i < 8) {
            tone(b, i * 0.06, 0.07, 330.0 + i * 70.0, 330.0 + i * 70.0, 0.3, 0);
            i = i + 1;
        }
        paths[2] = save("sprout", b);

        b = fx(0.35);                                            // 3 brick break
        tone(b, 0.0, 0.3, 900.0, 120.0, 0.35, 3);
        paths[3] = save("break", b);

        b = fx(0.12);                                            // 4 bump
        tone(b, 0.0, 0.1, 140.0, 90.0, 0.45, 0);
        paths[4] = save("bump", b);

        b = fx(0.3);                                             // 5 enemy knocked off
        tone(b, 0.0, 0.06, 600.0, 400.0, 0.3, 0);
        tone(b, 0.06, 0.06, 400.0, 250.0, 0.3, 0);
        tone(b, 0.12, 0.15, 260.0, 120.0, 0.3, 0);
        paths[5] = save("kick", b);

        b = fx(1.2);                                             // 6 flag slide + fanfare
        tone(b, 0.0, 0.7, 1500.0, 200.0, 0.25, 2);
        int[] fan = new int[]{0, 4, 7, 12, 7, 12};
        i = 0;
        while (i < 6) {
            tone(b, 0.72 + i * 0.08, 0.1, freq(fan[i] + 12), freq(fan[i] + 12), 0.28, 0);
            i = i + 1;
        }
        paths[6] = save("flag", b);

        b = fx(0.7);                                             // 7 1-up
        int[] up = new int[]{7, 11, 16, 14, 18, 23};
        i = 0;
        while (i < 6) {
            tone(b, i * 0.09, 0.1, freq(up[i] + 12), freq(up[i] + 12), 0.28, 1);
            i = i + 1;
        }
        paths[7] = save("oneup", b);

        b = fx(0.2);                                             // 8 stomp
        tone(b, 0.0, 0.14, 700.0, 180.0, 0.4, 1);
        paths[8] = save("stomp", b);

        b = fx(0.55);                                            // 9 shrink
        i = 0;
        while (i < 6) {
            tone(b, i * 0.07, 0.08, 800.0 - i * 90.0, 700.0 - i * 90.0, 0.28, 0);
            i = i + 1;
        }
        paths[9] = save("shrink", b);

        b = fx(2.2);                                             // 10 death
        int[] die = new int[]{12, 11, 10, 9, 7, 5, 3, 0};
        i = 0;
        while (i < 8) {
            tone(b, 0.35 + i * 0.13, 0.13, freq(die[i]), freq(die[i] - 1), 0.3, 0);
            i = i + 1;
        }
        tone(b, 0.0, 0.3, 520.0, 520.0, 0.2, 0);
        paths[10] = save("die", b);

        b = fx(0.6);                                             // 11 power-up collected
        int[] pw = new int[]{0, 4, 7, 12, 16, 19, 12, 16, 19, 24};
        i = 0;
        while (i < 10) {
            tone(b, i * 0.05, 0.06, freq(pw[i] + 12), freq(pw[i] + 12), 0.28, 0);
            i = i + 1;
        }
        paths[11] = save("power", b);
    }

    /**
     * Original tunes. kind 0 overworld (arpeggio over a four-chord cycle), 1 underground
     * (slow, sparse, minor), 2 starman (fast and bright).
     */
    static void buildMusic() {
        int kind = 0;
        while (kind < 3) {
            double stepSec = 0.2143;
            int bars = 16;
            int[] roots = new int[]{0, 7, 9, 5};
            int[] minor = new int[]{0, 0, 1, 0};
            double melVol = 0.13;
            int melWave = 1;
            if (kind == 1) {
                stepSec = 0.30;
                bars = 8;
                roots = new int[]{0, 0, -2, -2};
                minor = new int[]{1, 1, 0, 0};
                melVol = 0.09;
                melWave = 2;
            } else if (kind == 2) {
                stepSec = 0.105;
                bars = 16;
                roots = new int[]{0, 5, 7, 5};
                minor = new int[]{0, 0, 0, 0};
                melVol = 0.12;
            }
            double[] b = fx(bars * 8 * stepSec + 0.05);
            int[][] pats = new int[4][];
            pats[0] = new int[]{0, 1, 2, 3, 2, 1, 2, 1};
            pats[1] = new int[]{2, 3, 4, 3, 2, 1, 0, 1};
            pats[2] = new int[]{0, 2, 1, 3, 2, 4, 3, 2};
            pats[3] = new int[]{3, 2, 1, 2, 3, 4, 5, 3};
            int bar = 0;
            while (bar < bars) {
                int chord = bar % 4;
                int root = roots[chord];
                int third = 4;
                if (minor[chord] != 0) {
                    third = 3;
                }
                int[] tones = new int[]{root, root + third, root + 7};
                int[] pat = pats[(bar / 2) % 4];
                int s = 0;
                while (s < 8) {
                    double t0 = (bar * 8 + s) * stepSec;
                    int n = pat[s];
                    int semi = tones[n % 3] + 12 * (n / 3) + 12;
                    int rest = 0;
                    if (kind == 0 && bar % 8 == 7 && s >= 6) {
                        rest = 1;
                    }
                    if (kind == 1 && s % 2 == 1) {
                        rest = 1;
                    }
                    if (kind == 2) {
                        semi = semi + 12;
                    }
                    if (rest == 0) {
                        tone(b, t0, stepSec * 0.9, freq(semi), freq(semi), melVol, melWave);
                    }
                    int bassStep = 0;
                    if (s == 0 || s == 2 || s == 4 || s == 6) {
                        bassStep = 1;
                    }
                    if (kind == 1 && s != 0 && s != 4) {
                        bassStep = 0;
                    }
                    if (bassStep != 0) {
                        int bs = root - 12;
                        if (s == 4) {
                            bs = root - 5;
                        }
                        tone(b, t0, stepSec * 1.8, freq(bs), freq(bs), 0.22, 2);
                    }
                    if (kind != 1 && s % 2 == 1) {
                        tone(b, t0, 0.03, 0.0, 0.0, 0.05, 3);
                    }
                    s = s + 1;
                }
                bar = bar + 1;
            }
            musicSecondsOf[kind] = bars * 8 * stepSec;
            musicPaths[kind] = save("music" + kind, b);
            kind = kind + 1;
        }
        musicTrack = 0;
        musicSeconds = musicSecondsOf[0];
        musicPath = musicPaths[0];
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

    // ------------------------------------------------------------------ playback

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

    static void play(int id) {
        if (enabled == 0 || id < 0 || id >= count) {
            return;
        }
        reap();
        if (running.size() > 12) {
            return;
        }
        Process p = new ProcessBuilder("/usr/bin/afplay").argument("-v").argument("0.6").argument(paths[id]).start();
        if (p != null) {
            running.add(p);
        }
    }

    static void musicStart() {
        musicStartTrack(0);
    }

    static void musicStartTrack(int kind) {
        if (enabled == 0) {
            return;
        }
        if (kind < 0 || kind > 2) {
            kind = 0;
        }
        musicStop();
        musicTrack = kind;
        musicSeconds = musicSecondsOf[kind];
        musicPath = musicPaths[kind];
        musicOn = 1;
        spawnMusic();
    }

    static void spawnMusic() {
        musicProc = new ProcessBuilder("/usr/bin/afplay").argument("-v").argument("0.35").argument(musicPath).start();
        musicStartMs = System.currentTimeMillis();
    }

    static void musicStop() {
        musicOn = 0;
        if (musicProc != null) {
            musicProc.destroy();
            musicProc = null;
        }
    }

    /** Called every frame: restarts the tune when it finishes. */
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
