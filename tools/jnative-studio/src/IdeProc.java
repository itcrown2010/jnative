import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

interface IdeLineHandler {
    void line(String text);
}

interface IdeExitHandler {
    void exited(int code);
}

/**
 * A child process whose merged stdout/stderr is pumped into line callbacks on the UI thread by a
 * timer (used for building and for running the app under development).
 *
 * @author 张晓达
 */
public class IdeProc {
    Process proc;
    UiTimer timer;
    String partial = "";
    IdeLineHandler onLine;
    IdeExitHandler onExit;
    boolean done = false;
    boolean stopped = false;

    /** Starts "/bin/sh -c command" in dir. Returns false when the process could not be spawned. */
    boolean start(String dir, String command) {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh");
        pb.argument("-c");
        pb.argument("cd '" + dir + "' && " + command);
        proc = pb.start();
        if (proc == null) {
            return false;
        }
        timer = new UiTimer(60, () -> pump());
        return true;
    }

    void pump() {
        if (done || proc == null) {
            return;
        }
        int rounds = 0;
        boolean eof = false;
        while (rounds < 40) {
            String s = proc.readStdoutNb(8192, 0);
            if (s == null) {
                eof = true;
                break;
            }
            if (s.length() == 0) {
                break;
            }
            feed(s);
            rounds = rounds + 1;
        }
        int code = proc.pollExit();
        if (code != -1000 || eof) {
            if (code == -1000) {
                code = proc.waitFor();
            }
            // drain whatever is left
            int guard = 0;
            while (guard < 200) {
                String s2 = proc.readStdoutNb(8192, 0);
                if (s2 == null || s2.length() == 0) {
                    break;
                }
                feed(s2);
                guard = guard + 1;
            }
            if (partial.length() > 0) {
                emit(partial);
                partial = "";
            }
            done = true;
            timer.stop();
            if (onExit != null) {
                onExit.exited(code);
            }
        }
    }

    void feed(String s) {
        String all = partial + s;
        int start = 0;
        int i = 0;
        while (i < all.length()) {
            if (all.charAt(i) == '\n') {
                String ln = all.substring(start, i);
                if (ln.endsWith("\r")) {
                    ln = ln.substring(0, ln.length() - 1);
                }
                emit(ln);
                start = i + 1;
            }
            i = i + 1;
        }
        partial = all.substring(start);
    }

    void emit(String ln) {
        if (onLine != null) {
            onLine.line(ln);
        }
    }

    boolean running() {
        return proc != null && !done;
    }

    void stop() {
        if (proc != null && !done) {
            stopped = true;
            proc.destroy();
        }
    }
}
