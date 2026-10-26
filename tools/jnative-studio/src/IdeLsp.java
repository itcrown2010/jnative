import jnative.ui.*;
import jnative.io.*;

/** Receives the result of a language-service request: the JSON "result" value, or null on error / timeout. */
interface IdeLspHandler {
    void done(JsonValue result);
}

/**
 * Client for the JNative language service ({@code jnative lsp --ascii}): a long-lived child process spoken to over
 * stdin / stdout with LSP framing. Every message is 7-bit (non-ASCII is sent and received as \\uXXXX), so the
 * Content-Length of a message equals its character count. Requests are asynchronous: a timer pumps the pipes and
 * calls the handlers on the UI thread; results for a document version that has since changed are dropped by the caller.
 *
 * @author 张晓达
 */
public class IdeLsp {
    Process proc;
    UiTimer timer;
    String inbuf = "";
    String errTail = "";
    int nextId = 1;
    boolean ready = false;
    boolean dead = false;
    String root = "";
    ArrayList<Integer> pendingIds = new ArrayList<Integer>();
    ArrayList<IdeLspHandler> pendingHandlers = new ArrayList<IdeLspHandler>();
    ArrayList<String> openPaths = new ArrayList<String>();
    ArrayList<Integer> openVersions = new ArrayList<Integer>();
    ArrayList<String> openTexts = new ArrayList<String>();
    int initId = 0;

    // ---------------------------------------------------------------- lifecycle

    /** Starts the service for a project directory. Returns false when the process could not be spawned. */
    boolean start(String projectDir) {
        stop();
        root = projectDir;
        dead = false;
        ready = false;
        inbuf = "";
        String cmd;
        if (IdeSettings.compiler.endsWith(".jar")) {
            cmd = "exec " + IdeSettings.javaCmd + " -jar '" + IdeSettings.compiler + "' lsp --ascii";
        } else {
            cmd = "exec " + IdeSettings.javaCmd + " -cp '" + IdeSettings.compiler + "' io.jnative.Main lsp --ascii";
        }
        ProcessBuilder pb = new ProcessBuilder("/bin/sh");
        pb.argument("-c");
        pb.argument("cd '" + projectDir + "' && " + cmd);
        pb.pipeStdin();
        proc = pb.start();
        if (proc == null) {
            dead = true;
            return false;
        }
        timer = new UiTimer(30, () -> pump());
        initId = send("initialize", "{\"processId\":null,\"rootUri\":" + jstr("file://" + projectDir)
                + ",\"capabilities\":{},\"initializationOptions\":{\"formatIndentSize\":4}}", r -> initialized(r));
        return true;
    }

    void initialized(JsonValue r) {
        ready = true;
        notifyServer("initialized", "{}");
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
            if (!dead) {
                notifyServer("shutdown", "null");
                notifyServer("exit", "null");
            }
            proc.closeStdin();
            proc.destroy();
            proc.waitFor();
            proc = null;
        }
        dead = true;
        ready = false;
        failAll();
        openPaths = new ArrayList<String>();
        openVersions = new ArrayList<Integer>();
        openTexts = new ArrayList<String>();
    }

    void failAll() {
        ArrayList<IdeLspHandler> hs = pendingHandlers;
        pendingIds = new ArrayList<Integer>();
        pendingHandlers = new ArrayList<IdeLspHandler>();
        int i = 0;
        while (i < hs.size()) {
            hs.get(i).done(null);
            i = i + 1;
        }
    }

    // ---------------------------------------------------------------- document sync

    /** Makes the server's copy of a file equal to text (didOpen the first time, a full didChange afterwards). */
    void sync(String path, String text) {
        if (!running()) {
            return;
        }
        int idx = openPaths.indexOf(path);
        String uri = jstr("file://" + path);
        if (idx < 0) {
            openPaths.add(path);
            openVersions.add(1);
            openTexts.add(text);
            notifyServer("textDocument/didOpen", "{\"textDocument\":{\"uri\":" + uri
                    + ",\"languageId\":\"jnative\",\"version\":1,\"text\":" + jstr(text) + "}}");
            return;
        }
        if (openTexts.get(idx).equals(text)) {
            return;
        }
        int v = openVersions.get(idx) + 1;
        openVersions.set(idx, v);
        openTexts.set(idx, text);
        notifyServer("textDocument/didChange", "{\"textDocument\":{\"uri\":" + uri + ",\"version\":" + v
                + "},\"contentChanges\":[{\"text\":" + jstr(text) + "}]}");
    }

    int versionOf(String path) {
        int idx = openPaths.indexOf(path);
        if (idx < 0) {
            return 0;
        }
        return openVersions.get(idx);
    }

    void closeDoc(String path) {
        int idx = openPaths.indexOf(path);
        if (idx < 0 || !running()) {
            return;
        }
        openPaths.remove(idx);
        openVersions.remove(idx);
        openTexts.remove(idx);
        notifyServer("textDocument/didClose", "{\"textDocument\":{\"uri\":" + jstr("file://" + path) + "}}");
    }

    // ---------------------------------------------------------------- requests

    /** Position-based request on a synced document. line / col are 0-based. */
    int requestAt(String method, String path, int line, int col, String extraParams, IdeLspHandler h) {
        String p = "{\"textDocument\":{\"uri\":" + jstr("file://" + path) + "},\"position\":{\"line\":" + line
                + ",\"character\":" + col + "}" + extraParams + "}";
        return send(method, p, h);
    }

    int send(String method, String params, IdeLspHandler h) {
        if (!running()) {
            h.done(null);
            return 0;
        }
        int id = nextId;
        nextId = nextId + 1;
        pendingIds.add(id);
        pendingHandlers.add(h);
        write("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}");
        return id;
    }

    void notifyServer(String method, String params) {
        write("{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + params + "}");
    }

    void write(String json) {
        if (proc == null || dead) {
            return;
        }
        boolean ok = proc.writeStdin("Content-Length: " + json.length() + "\r\n\r\n" + json);
        if (!ok) {
            dead = true;
            failAll();
        }
    }

    /** Drops a request's handler (the answer, if it still arrives, is ignored). */
    void cancel(int id) {
        int i = pendingIds.indexOf(id);
        if (i >= 0) {
            pendingIds.remove(i);
            pendingHandlers.remove(i);
        }
    }

    // ---------------------------------------------------------------- reading

    void pump() {
        if (proc == null) {
            return;
        }
        int rounds = 0;
        boolean eof = false;
        while (rounds < 20) {
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
        // keep the stderr pipe drained: a full pipe would block the server
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
        if (eof || proc.pollExit() != -1000) {
            dead = true;
            ready = false;
            timer.stop();
            failAll();
        }
    }

    void drain() {
        int guard = 0;
        while (guard < 50) {
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
            int len = parseInt(num.trim());
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
        if (!o.has("id") || o.has("method")) {
            return; // notification or server request: nothing to do yet
        }
        int id = o.getInt("id", -1);
        int i = pendingIds.indexOf(id);
        if (i < 0) {
            return;
        }
        IdeLspHandler h = pendingHandlers.get(i);
        pendingIds.remove(i);
        pendingHandlers.remove(i);
        JsonValue res = o.get("result");
        if (o.has("error") || res == null || res.isNull()) {
            h.done(null);
        } else {
            h.done(res);
        }
    }

    // ---------------------------------------------------------------- text helpers

    static int parseInt(String s) {
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n * 10 + (c - 48);
            }
            i = i + 1;
        }
        return n;
    }

    static String hex4(int v) {
        String d = "0123456789abcdef";
        return "\\u" + d.substring((v >> 12) & 15, ((v >> 12) & 15) + 1) + d.substring((v >> 8) & 15, ((v >> 8) & 15) + 1)
                + d.substring((v >> 4) & 15, ((v >> 4) & 15) + 1) + d.substring(v & 15, (v & 15) + 1);
    }

    /** JSON string literal with every non-ASCII character as \\uXXXX (surrogate pairs above U+FFFF). */
    static String jstr(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                sb.append("\\\"");
            } else if (c == 92) {
                sb.append("\\\\");
            } else if (c == 10) {
                sb.append("\\n");
            } else if (c == 13) {
                sb.append("\\r");
            } else if (c == 9) {
                sb.append("\\t");
            } else if (c < 32 || c > 126) {
                if (c > 65535) {
                    int u = c - 65536;
                    sb.append(hex4(55296 + (u >> 10)));
                    sb.append(hex4(56320 + (u & 1023)));
                } else {
                    sb.append(hex4(c));
                }
            } else {
                sb.append(s.substring(i, i + 1));
            }
            i = i + 1;
        }
        sb.append("\"");
        return sb.toString();
    }
}
