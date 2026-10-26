import jnative.ui.*;
import jnative.io.*;

/** Receives the response of a debug-adapter request. */
interface IdeDapHandler {
    void done(boolean ok, JsonObject body);
}

/** Receives debug-adapter events (stopped, output, terminated ...). */
interface IdeDapEvents {
    void event(String name, JsonObject body);

    void closed();
}

/**
 * Client for a Debug Adapter Protocol server ({@code lldb-dap}). The adapter runs behind a tiny perl filter that
 * rewrites every message to 7-bit ASCII (non-ASCII as \\uXXXX, Content-Length corrected), so the framing here can
 * count characters instead of bytes. A timer pumps the pipes; handlers and events run on the UI thread.
 *
 * @author 张晓达
 */
public class IdeDap {
    static String SHIM = "$|=1; binmode(STDIN); binmode(STDOUT);\n"
            + "while (1) {\n"
            + "  my $h = ''; my $c;\n"
            + "  while ($h !~ /\\r\\n\\r\\n$/) { read(STDIN, $c, 1) or exit 0; $h .= $c; }\n"
            + "  $h =~ /Content-Length:\\s*(\\d+)/i or next;\n"
            + "  my $len = $1; my $b = '';\n"
            + "  while (length($b) < $len) { my $n = read(STDIN, $c, $len - length($b)); $n or exit 0; $b .= $c; }\n"
            + "  utf8::decode($b);\n"
            + "  $b =~ s/([^\\x00-\\x7e])/my $o = ord($1); $o > 0xFFFF ? sprintf('\\\\u%04x\\\\u%04x', 0xD800 + (($o - 0x10000) >> 10), "
            + "0xDC00 + (($o - 0x10000) & 0x3FF)) : sprintf('\\\\u%04x', $o)/ge;\n"
            + "  print 'Content-Length: ' . length($b) . \"\\r\\n\\r\\n\" . $b;\n"
            + "}\n";

    Process proc;
    UiTimer timer;
    String inbuf = "";
    String errTail = "";
    int nextSeq = 1;
    boolean dead = true;
    int busy = 0;
    IdeDapEvents events;
    ArrayList<Integer> pendingIds = new ArrayList<Integer>();
    ArrayList<IdeDapHandler> pendingHandlers = new ArrayList<IdeDapHandler>();

    /** Finds an lldb-dap executable (Xcode, Command Line Tools, Homebrew LLVM) or returns "". */
    static String findAdapter() {
        String[] c = new String[5];
        c[0] = "/Applications/Xcode.app/Contents/Developer/usr/bin/lldb-dap";
        c[1] = "/Library/Developer/CommandLineTools/usr/bin/lldb-dap";
        c[2] = "/opt/homebrew/opt/llvm/bin/lldb-dap";
        c[3] = "/usr/local/opt/llvm/bin/lldb-dap";
        c[4] = "/usr/bin/lldb-dap";
        int i = 0;
        while (i < c.length) {
            if (Files.exists(FsPath.of(c[i]))) {
                return c[i];
            }
            i = i + 1;
        }
        return "";
    }

    boolean start(String adapter, IdeDapEvents ev) {
        stop();
        events = ev;
        inbuf = "";
        nextSeq = 1;
        ProcessBuilder pb = new ProcessBuilder(adapter);
        pb.pipeStdin();
        proc = pb.start();
        if (proc == null) {
            return false;
        }
        dead = false;
        timer = new UiTimer(30, () -> pump());
        return true;
    }

    boolean running() {
        return proc != null && !dead;
    }

    void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (proc != null) {
            proc.closeStdin();
            proc.destroy();
            proc.waitFor();
            proc = null;
        }
        dead = true;
        pendingIds = new ArrayList<Integer>();
        pendingHandlers = new ArrayList<IdeDapHandler>();
    }

    int request(String command, String argsJson, IdeDapHandler h) {
        if (!running()) {
            if (h != null) {
                h.done(false, null);
            }
            return 0;
        }
        int id = nextSeq;
        nextSeq = nextSeq + 1;
        if (h != null) {
            pendingIds.add(id);
            pendingHandlers.add(h);
        }
        pump();
        String json = "{\"seq\":" + id + ",\"type\":\"request\",\"command\":\"" + command + "\",\"arguments\":" + argsJson + "}";
        boolean ok = proc.writeStdin("Content-Length: " + json.length() + "\r\n\r\n" + json);
        if (!ok) {
            dead = true;
        }
        return id;
    }

    void pump() {
        if (proc == null || busy != 0) {
            return;
        }
        busy = 1;
        int rounds = 0;
        boolean eof = false;
        while (rounds < 80) {
            String s = proc.readStdoutNb(65536, 0);
            if (s == null) {
                eof = true;
                break;
            }
            if (s.length() == 0) {
                break;
            }
            inbuf = inbuf + s;
            rounds = rounds + 1;
        }
        int er = 0;
        while (er < 4) {
            String e = proc.readStderrNb(8192, 0);
            if (e == null || e.length() == 0) {
                break;
            }
            errTail = errTail + e;
            if (errTail.length() > 4000) {
                errTail = errTail.substring(errTail.length() - 2000);
            }
            er = er + 1;
        }
        drain();
        busy = 0;
        if (eof || proc.pollExit() != -1000) {
            dead = true;
            if (timer != null) {
                timer.stop();
            }
            IdeDapEvents ev = events;
            if (ev != null) {
                ev.closed();
            }
        }
    }

    void drain() {
        int guard = 0;
        while (guard < 200) {
            int he = inbuf.indexOf("\r\n\r\n");
            if (he < 0) {
                return;
            }
            String head = inbuf.substring(0, he);
            int cl = head.indexOf("Content-Length:");
            if (cl < 0) {
                inbuf = inbuf.substring(he + 4);
                guard = guard + 1;
                continue;
            }
            int e2 = head.indexOf("\r", cl);
            String num = e2 < 0 ? head.substring(cl + 15) : head.substring(cl + 15, e2);
            int len = IdeLsp.parseInt(num.trim());
            if (inbuf.length() < he + 4 + len) {
                return;
            }
            String body = inbuf.substring(he + 4, he + 4 + len);
            inbuf = inbuf.substring(he + 4 + len);
            handle(body);
            guard = guard + 1;
        }
    }

    void handle(String body) {
        JsonValue v = Json.parse(body);
        if (v == null || !v.isObject()) {
            return;
        }
        JsonObject o = v.asObject();
        if (errTail.length() < 2000) {
            errTail = errTail + "\nMSG " + body.substring(0, body.length() < 180 ? body.length() : 180);
        }
        String type = o.getString("type", "");
        if (type.equals("event")) {
            JsonValue b = o.get("body");
            JsonObject bo = b != null && b.isObject() ? b.asObject() : new JsonObject();
            if (events != null) {
                events.event(o.getString("event", ""), bo);
            }
            return;
        }
        if (!type.equals("response")) {
            return;
        }
        int id = o.getInt("request_seq", -1);
        int i = pendingIds.indexOf(id);
        if (i < 0) {
            return;
        }
        IdeDapHandler h = pendingHandlers.get(i);
        pendingIds.remove(i);
        pendingHandlers.remove(i);
        JsonValue b = o.get("body");
        JsonObject bo = b != null && b.isObject() ? b.asObject() : new JsonObject();
        JsonValue sv = o.get("success");
        h.done(sv.kind == JsonValue.BOOL_KIND && sv.asBoolean(), bo);
    }
}
