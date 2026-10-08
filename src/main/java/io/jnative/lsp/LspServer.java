package io.jnative.lsp;

import io.jnative.compiler.Compilation;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.SourceLocation;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal stdio Language Server for JNative frontend diagnostics.
 * Protocol messages go on stdout; all logs go to stderr.
 * Analyze-only — never links or runs programs.
 */
public final class LspServer {
    private final InputStream in;
    private final OutputStream out;
    private final JNativeCompiler compiler = new JNativeCompiler();
    private final Map<String, DocState> docs = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jnative-lsp-analyze");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object writeLock = new Object();

    public LspServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        System.err.println("jnative lsp: starting (stdio)");
        new LspServer(System.in, System.out).run();
    }

    public void run() throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        while (running.get()) {
            String body = readMessage(bin);
            if (body == null) {
                break;
            }
            handleMessage(body);
        }
        workers.shutdownNow();
        System.err.println("jnative lsp: exit");
    }

    private void handleMessage(String body) throws IOException {
        String method = extractString(body, "method");
        Long id = extractId(body);
        if (method == null) {
            return;
        }
        switch (method) {
            case "initialize" -> writeResult(id, initializeResult());
            case "initialized" -> { /* ack */ }
            case "shutdown" -> {
                running.set(false);
                writeResult(id, "null");
            }
            case "exit" -> running.set(false);
            case "textDocument/didOpen" -> onDidOpen(body);
            case "textDocument/didChange" -> onDidChange(body);
            case "textDocument/didClose" -> onDidClose(body);
            default -> {
                if (id != null) {
                    writeError(id, -32601, "Method not found: " + method);
                }
            }
        }
    }

    private String initializeResult() {
        return "{"
                + "\"capabilities\":{"
                + "\"textDocumentSync\":1"
                + "},"
                + "\"serverInfo\":{\"name\":\"jnative-lsp\",\"version\":\"" + io.jnative.Main.VERSION + "\"}"
                + "}";
    }

    private void onDidOpen(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        String text = extractNestedString(body, "textDocument", "text");
        int version = extractNestedInt(body, "textDocument", "version", 0);
        if (uri == null || text == null) {
            return;
        }
        docs.put(uri, new DocState(uri, text, version));
        scheduleAnalyze(uri, text, version);
    }

    private void onDidChange(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int version = extractNestedInt(body, "textDocument", "version", 0);
        // Full-document sync: take last content change text
        String text = extractLastChangeText(body);
        if (uri == null || text == null) {
            return;
        }
        docs.put(uri, new DocState(uri, text, version));
        scheduleAnalyze(uri, text, version);
    }

    private void onDidClose(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        if (uri == null) {
            return;
        }
        docs.remove(uri);
        publishDiagnostics(uri, List.of());
    }

    private void scheduleAnalyze(String uri, String text, int version) {
        workers.execute(() -> {
            DocState cur = docs.get(uri);
            if (cur == null || cur.version != version) {
                return; // stale
            }
            try {
                Thread.sleep(200); // debounce
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            cur = docs.get(uri);
            if (cur == null || cur.version != version) {
                return;
            }
            String path = uriToPath(uri);
            Compilation c = compiler.analyze(SourceUnit.ofPath(Path.of(path), text), version);
            cur = docs.get(uri);
            if (cur == null || cur.version != version) {
                return;
            }
            publishDiagnostics(uri, c.diagnostics());
        });
    }

    private void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
        StringBuilder items = new StringBuilder();
        items.append('[');
        boolean first = true;
        for (Diagnostic d : diagnostics) {
            if (!first) {
                items.append(',');
            }
            first = false;
            SourceLocation loc = d.location() == null ? SourceLocation.UNKNOWN : d.location();
            int line = Math.max(0, loc.line() - 1); // LSP is 0-based
            int col = Math.max(0, loc.column() <= 0 ? 0 : loc.column() - 1);
            int severity = switch (d.severity()) {
                case ERROR -> 1;
                case WARNING -> 2;
                case NOTE -> 3;
            };
            String msg = d.message();
            if ("<stdlib>".equals(loc.file())) {
                msg = "[stdlib] " + msg;
            }
            items.append('{')
                    .append("\"range\":{\"start\":{\"line\":").append(line)
                    .append(",\"character\":").append(col)
                    .append("},\"end\":{\"line\":").append(line)
                    .append(",\"character\":").append(col + 1).append("}},")
                    .append("\"severity\":").append(severity).append(',')
                    .append("\"source\":\"jnative\",")
                    .append("\"message\":").append(jsonString(msg))
                    .append('}');
        }
        items.append(']');
        String note = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":{"
                + "\"uri\":" + jsonString(uri) + ","
                + "\"diagnostics\":" + items
                + "}}";
        try {
            writeRaw(note);
        } catch (IOException e) {
            System.err.println("jnative lsp: publish failed: " + e.getMessage());
        }
    }

    private static String uriToPath(String uri) {
        if (uri == null) {
            return "Memory.java";
        }
        if (uri.startsWith("file://")) {
            String rest = uri.substring("file://".length());
            // file:///Users/... or file://localhost/Users
            if (rest.startsWith("localhost")) {
                rest = rest.substring("localhost".length());
            }
            try {
                return java.net.URLDecoder.decode(rest, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return rest;
            }
        }
        return uri;
    }

    private String readMessage(BufferedInputStream bin) throws IOException {
        Map<String, String> headers = new HashMap<>();
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        while (true) {
            int b = bin.read();
            if (b < 0) {
                return null;
            }
            if (b == '\n') {
                String line = lineBuf.toString(StandardCharsets.US_ASCII).replace("\r", "");
                lineBuf.reset();
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).trim());
                }
            } else {
                lineBuf.write(b);
            }
        }
        String lenStr = headers.get("content-length");
        if (lenStr == null) {
            return null;
        }
        int len = Integer.parseInt(lenStr);
        byte[] body = bin.readNBytes(len);
        if (body.length < len) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private void writeResult(Long id, String resultJson) throws IOException {
        if (id == null) {
            return;
        }
        writeRaw("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}");
    }

    private void writeError(Long id, int code, String message) throws IOException {
        writeRaw("{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"error\":{\"code\":" + code + ",\"message\":" + jsonString(message) + "}}");
    }

    private void writeRaw(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            String header = "Content-Length: " + bytes.length + "\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + pattern.length());
        if (colon < 0) {
            return null;
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        return unescape(readQuoted(json, q1));
    }

    private static Long extractId(String json) {
        int i = json.indexOf("\"id\"");
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + 4);
        if (colon < 0) {
            return null;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        if (j < json.length() && json.charAt(j) == '"') {
            return null;
        }
        int k = j;
        while (k < json.length() && (Character.isDigit(json.charAt(k)))) {
            k++;
        }
        if (k == j) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(j, k));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractNestedString(String json, String obj, String key) {
        int objPos = json.indexOf("\"" + obj + "\"");
        if (objPos < 0) {
            return null;
        }
        return extractString(json.substring(objPos), key);
    }

    private static int extractNestedInt(String json, String obj, String key, int def) {
        int objPos = json.indexOf("\"" + obj + "\"");
        if (objPos < 0) {
            return def;
        }
        String slice = json.substring(objPos);
        String pattern = "\"" + key + "\"";
        int i = slice.indexOf(pattern);
        if (i < 0) {
            return def;
        }
        int colon = slice.indexOf(':', i + pattern.length());
        if (colon < 0) {
            return def;
        }
        int j = colon + 1;
        while (j < slice.length() && Character.isWhitespace(slice.charAt(j))) {
            j++;
        }
        int k = j;
        while (k < slice.length() && (Character.isDigit(slice.charAt(k)) || slice.charAt(k) == '-')) {
            k++;
        }
        try {
            return Integer.parseInt(slice.substring(j, k));
        } catch (Exception e) {
            return def;
        }
    }

    private static String extractLastChangeText(String json) {
        // Prefer contentChanges[0].text for full sync
        int idx = json.lastIndexOf("\"text\"");
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + 6);
        if (colon < 0) {
            return null;
        }
        int q = json.indexOf('"', colon + 1);
        if (q < 0) {
            return null;
        }
        return unescape(readQuoted(json, q));
    }

    private static String readQuoted(String json, int openQuote) {
        StringBuilder sb = new StringBuilder();
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append('\\').append(json.charAt(++i));
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        }
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private record DocState(String uri, String text, int version) {
    }
}
