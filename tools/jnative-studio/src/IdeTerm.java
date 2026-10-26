import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;
import jnative.io.*;

/**
 * Integrated terminal: one long-lived shell (stdin pipe, stdout and stderr merged) shown as a scrolling text canvas with
 * a one-line input. Commands run in the project directory and the shell keeps its state (cd, exported variables).
 * There is no pseudo terminal, so full-screen programs (vim, top) are not supported; line-based programs work, and
 * while a command is running whatever you type goes to its stdin. ⌃C interrupts the running command, ⌘K clears,
 * ↑ / ↓ browse the history, ⌘V pastes.
 *
 * @author 张晓达
 */
public class IdeTerm extends Canvas {
    IdeApp app;
    Process proc;
    UiTimer timer;
    ArrayList<String> lines = new ArrayList<String>();
    String partial = "";
    String input = "";
    String composing = "";
    String cwd = "";
    String home = "";
    ArrayList<String> history = new ArrayList<String>();
    int histPos = 0;
    /** Commands sent whose end marker has not been seen yet. */
    int pending = 0;
    int scrollLine = -1;
    int rowH = 17;
    boolean termFocus = false;
    boolean started = false;
    int cursorBlink = 0;

    IdeTerm(IdeApp a) {
        app = a;
        setHiDpi(true);
        onPaint(g -> paintTerm(g));
        onKeyDown(e -> keyTerm(e));
        onTextInput(e -> textTerm(e));
        onMouseWheel(e -> wheelTerm(e));
        onMouseDown(e -> requestFocus());
        onFocus(e -> focusTerm(e));
        UiNative.canvasSetTextInput(nativeId, 1);
        IdeApp.vis(this, false);
    }

    // ---------------------------------------------------------------- shell process

    boolean running() {
        return proc != null && proc.isAlive();
    }

    /** Starts the shell in the project directory when it is not running. */
    void ensureStarted() {
        if (running()) {
            return;
        }
        if (app.project == null) {
            return;
        }
        cwd = app.project.dir;
        home = Env.getOr("HOME", "");
        ProcessBuilder pb = new ProcessBuilder("/bin/sh");
        pb.argument("-c");
        pb.argument("cd '" + cwd + "' && exec /bin/zsh -f 2>&1");
        pb.pipeStdin();
        proc = pb.start();
        pending = 0;
        partial = "";
        if (proc == null) {
            addLine("无法启动 /bin/zsh");
            return;
        }
        started = true;
        proc.writeStdin("exec 2>&1\n");
        if (timer == null) {
            timer = new UiTimer(40, () -> pump());
        }
        addLine("JNative Studio 终端 · " + cwd);
    }

    void restart() {
        if (proc != null) {
            killChildren();
            proc.closeStdin();
            proc.destroy();
            proc.waitFor();
            proc = null;
        }
        ensureStarted();
        repaint();
    }

    void shutdown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (proc != null) {
            killChildren();
            proc.closeStdin();
            proc.destroy();
            proc.waitFor();
            proc = null;
        }
    }

    /** SIGINT for everything the shell started (the shell itself keeps running). */
    void killChildren() {
        if (proc == null) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder("/bin/sh");
        pb.argument("-c");
        pb.argument("pkill -INT -P " + proc.pid + " 2>/dev/null; true");
        Process k = pb.start();
        if (k != null) {
            k.waitFor();
        }
    }

    void interrupt() {
        if (pending > 0) {
            killChildren();
            addLine("^C");
        } else {
            input = "";
            repaint();
        }
    }

    void pump() {
        if (proc == null) {
            return;
        }
        int rounds = 0;
        boolean got = false;
        boolean eof = false;
        while (rounds < 30) {
            String s = proc.readStdoutNb(16384, 0);
            if (s == null) {
                eof = true;
                break;
            }
            if (s.length() == 0) {
                break;
            }
            feed(s);
            got = true;
            rounds = rounds + 1;
        }
        String er = proc.readStderrNb(4096, 0);
        if (er != null && er.length() > 0) {
            feed(er);
            got = true;
        }
        if (eof || !proc.isAlive()) {
            if (partial.length() > 0) {
                addLine(partial);
                partial = "";
            }
            addLine("[shell 已退出，按回车重新启动]");
            proc = null;
            pending = 0;
            got = true;
        }
        if (got) {
            repaint();
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
                gotLine(ln);
                start = i + 1;
            }
            i = i + 1;
        }
        partial = all.substring(start);
        if (partial.length() > 4000) {
            gotLine(partial);
            partial = "";
        }
    }

    void gotLine(String ln) {
        int m = ln.indexOf("@@JNPWD:");
        if (m >= 0) {
            cwd = ln.substring(m + 8);
            if (pending > 0) {
                pending = pending - 1;
            }
            if (m > 0) {
                addLine(ln.substring(0, m));
            }
            return;
        }
        addLine(ln);
    }

    /** Removes ANSI escape sequences (colors, cursor moves) and other control characters, expands tabs. */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == 27) {
                i = i + 1;
                if (i < n && s.charAt(i) == '[') {
                    i = i + 1;
                    while (i < n && !((s.charAt(i) >= 64 && s.charAt(i) <= 126))) {
                        i = i + 1;
                    }
                } else if (i < n && s.charAt(i) == ']') {
                    while (i < n && s.charAt(i) != 7) {
                        i = i + 1;
                    }
                }
                i = i + 1;
                continue;
            }
            if (c == 9) {
                sb.append("    ");
            } else if (c == 13) {
                sb.setLength(0);
            } else if (c >= 32) {
                sb.append(c);
            }
            i = i + 1;
        }
        return sb.toString();
    }

    void addLine(String raw) {
        lines.add(clean(raw));
        if (lines.size() > 3000) {
            lines.removeAt(0);
            if (scrollLine > 0) {
                scrollLine = scrollLine - 1;
            }
        }
    }

    // ---------------------------------------------------------------- input

    String prompt() {
        String d = cwd;
        if (home.length() > 0 && d.startsWith(home)) {
            d = "~" + d.substring(home.length());
        }
        int sl = d.lastIndexOf("/");
        if (sl > 0 && sl < d.length() - 1 && d.length() > 24) {
            d = "…/" + d.substring(sl + 1);
        }
        return d + (pending > 0 ? " … " : " $ ");
    }

    void submit() {
        if (!running()) {
            addLine("");
            restart();
            input = "";
            return;
        }
        String cmd = input;
        input = "";
        scrollLine = -1;
        if (pending > 0) {
            addLine(cmd);
            proc.writeStdin(cmd + "\n");
        } else {
            addLine(prompt() + cmd);
            if (cmd.trim().length() > 0) {
                if (history.size() == 0 || !history.get(history.size() - 1).equals(cmd)) {
                    history.add(cmd);
                }
                if (cmd.trim().equals("clear")) {
                    lines.clear();
                    histPos = history.size();
                    repaint();
                    return;
                }
                pending = pending + 1;
                proc.writeStdin(cmd + "\n");
                proc.writeStdin("printf '@@JNPWD:%s\\n' \"$PWD\"\n");
            }
        }
        histPos = history.size();
        repaint();
    }

    void keyTerm(KeyEvent e) {
        int code = e.keyCode;
        boolean meta = e.metaDown != 0;
        if (e.ctrlDown != 0 && code == 67) {
            interrupt();
            return;
        }
        if (e.ctrlDown != 0 && code == 85) {
            input = "";
        } else if (meta && code == 75) {
            lines.clear();
            scrollLine = -1;
        } else if (meta && code == 86) {
            String t = Clipboard.getText();
            if (t != null) {
                t = t.replace("\r", "");
                int nl = t.indexOf("\n");
                input = input + (nl >= 0 ? t.substring(0, nl) : t);
            }
        } else if (meta && code == 67) {
            Clipboard.setText(lines.size() > 0 ? lines.get(lines.size() - 1) : "");
        } else if (code == KeyCode.ENTER) {
            submit();
            return;
        } else if (code == KeyCode.BACKSPACE) {
            if (input.length() > 0) {
                input = meta ? "" : input.substring(0, input.length() - 1);
            }
        } else if (code == KeyCode.UP) {
            if (history.size() > 0 && histPos > 0) {
                histPos = histPos - 1;
                input = history.get(histPos);
            }
        } else if (code == KeyCode.DOWN) {
            if (histPos < history.size() - 1) {
                histPos = histPos + 1;
                input = history.get(histPos);
            } else {
                histPos = history.size();
                input = "";
            }
        } else if (code == KeyCode.PAGE_UP) {
            scrollBy(-visibleRows() + 1);
            return;
        } else if (code == KeyCode.PAGE_DOWN) {
            scrollBy(visibleRows() - 1);
            return;
        } else if (!meta && e.ctrlDown == 0) {
            int ch = e.typedChar;
            if (ch >= 32 && ch != 127) {
                input = input + TextUtil.charString(ch);
            }
        }
        scrollLine = -1;
        repaint();
    }

    void textTerm(TextInputEvent e) {
        if (e.marked != 0) {
            composing = e.text;
            repaint();
            return;
        }
        composing = "";
        if (e.text.length() > 0) {
            input = input + e.text;
            scrollLine = -1;
        }
        repaint();
    }

    void focusTerm(FocusEvent e) {
        termFocus = e.gained != 0;
        if (termFocus) {
            ensureStarted();
        }
        repaint();
    }

    // ---------------------------------------------------------------- scrolling & painting

    int visibleRows() {
        return Math.max(1, height / rowH);
    }

    /** First line shown when the view is pinned to the bottom (one row is kept for the prompt). */
    int bottomTop() {
        return Math.max(0, lines.size() - (visibleRows() - 1));
    }

    void scrollBy(int d) {
        int top = scrollLine < 0 ? bottomTop() : scrollLine;
        top = Math.max(0, Math.min(bottomTop(), top + d));
        scrollLine = top >= bottomTop() ? -1 : top;
        repaint();
    }

    void wheelTerm(MouseEvent e) {
        scrollBy(-e.wheelDelta / 8 == 0 ? (e.wheelDelta > 0 ? -2 : 2) : -e.wheelDelta / 8);
    }

    void paintTerm(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, 0x1E2127, 0, 0, width, height);
        Font f = Ide.mono();
        int rows = visibleRows();
        int top = scrollLine < 0 ? bottomTop() : scrollLine;
        g.setClip(0, 0, width, height);
        int y = 4;
        int i = 0;
        while (i < rows - 1 && top + i < lines.size()) {
            String ln = lines.get(top + i);
            int col = 0xD7DAE0;
            if (ln.startsWith("^C") || ln.indexOf("error") >= 0 && ln.indexOf("error:") >= 0) {
                col = 0xF28B82;
            }
            Ide.textRow(g, ln, 10, y, rowH, f, col);
            y = y + rowH;
            i = i + 1;
        }
        if (scrollLine < 0 || top + i >= lines.size()) {
            String pr = prompt();
            String live = partial.length() > 0 ? clean(partial) : "";
            int x = 10;
            if (live.length() > 0) {
                Ide.textRow(g, live, x, y, rowH, f, 0xD7DAE0);
                x = x + Ide.textWidth(g, live, f);
            } else {
                Ide.textRow(g, pr, x, y, rowH, f, 0x7FC8A9);
                x = x + Ide.textWidth(g, pr, f);
            }
            String shownInput = input + composing;
            if (shownInput.length() > 0) {
                Ide.textRow(g, shownInput, x, y, rowH, f, 0xFFFFFF);
                x = x + Ide.textWidth(g, shownInput, f);
            }
            if (termFocus) {
                Ide.fill(g, 0xE0E3E8, x + 1, y + 2, 7, rowH - 4);
            }
        }
        g.clearClip();
        if (lines.size() > rows - 1) {
            int total = lines.size();
            double th = Math.max(20.0, height * (double) (rows - 1) / total);
            double ty = (height - th) * top / Math.max(1, total - (rows - 1));
            Ide.rfill(g, 0x5A6070, width - 6, ty + 2, 4, th - 4, 2);
        }
    }
}
