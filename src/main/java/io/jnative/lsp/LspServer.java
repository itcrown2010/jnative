/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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
 * Stdio Language Server for JNative: diagnostics, completion, navigation, hover, symbols,
 * limited code actions. Protocol on stdout; logs on stderr (never secrets / full env).
 * Analyze-only — never links or runs programs.
 * @author 张晓达
 */
public final class LspServer {
    private final InputStream in;
    private final OutputStream out;
    private final JNativeCompiler compiler = new JNativeCompiler();
    private final CompletionEngine completions = new CompletionEngine(compiler);
    private final IdeServices ide = new IdeServices(compiler);
    private final RenameService renamer = new RenameService(ide);
    private final Map<String, DocState> docs = new ConcurrentHashMap<>();
    private int formatIndent = 4;
    private boolean formatInsertSpaces = true;
    private final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jnative-lsp-analyze");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object writeLock = new Object();
    /** Latest scheduled diagnose version per URI — older tasks self-cancel. */
    private final ConcurrentHashMap<String, Integer> diagnoseGen = new ConcurrentHashMap<>();

    public LspServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        LspLog.info("starting (stdio) version=" + io.jnative.Main.VERSION
                + " (extension expects matching jnative.jar)");
        LspServer server = new LspServer(System.in, System.out);
        for (String a : args) {
            if ("--ascii".equals(a)) {
                server.asciiOnly = true; // clients that cannot count UTF-8 bytes (Studio): keep every message 7-bit
            }
        }
        server.run();
    }

    /** When set, every non-ASCII character in outgoing JSON is written as a \\uXXXX escape. */
    private boolean asciiOnly = false;

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
            case "initialize" -> {
                applyInitOptions(body);
                writeResult(id, initializeResult());
            }
            case "initialized" -> { /* ack */ }
            case "workspace/didChangeConfiguration" -> onDidChangeConfiguration(body);
            case "shutdown" -> {
                running.set(false);
                writeResult(id, "null");
            }
            case "exit" -> running.set(false);
            case "textDocument/didOpen" -> onDidOpen(body);
            case "textDocument/didChange" -> onDidChange(body);
            case "textDocument/didClose" -> onDidClose(body);
            case "workspace/didChangeWatchedFiles" -> onWatchedFiles(body);
            case "textDocument/completion" -> {
                if (id != null) {
                    writeResult(id, completionResult(body));
                }
            }
            case "textDocument/signatureHelp" -> {
                if (id != null) {
                    writeResult(id, signatureHelpResult(body));
                }
            }
            case "textDocument/definition" -> {
                if (id != null) {
                    writeResult(id, definitionResult(body));
                }
            }
            case "textDocument/references" -> {
                if (id != null) {
                    writeResult(id, referencesResult(body));
                }
            }
            case "textDocument/hover" -> {
                if (id != null) {
                    writeResult(id, hoverResult(body));
                }
            }
            case "textDocument/documentSymbol" -> {
                if (id != null) {
                    writeResult(id, documentSymbolResult(body));
                }
            }
            case "workspace/symbol" -> {
                if (id != null) {
                    writeResult(id, workspaceSymbolResult(body));
                }
            }
            case "textDocument/codeAction" -> {
                if (id != null) {
                    writeResult(id, codeActionResult(body));
                }
            }
            case "textDocument/prepareRename" -> {
                if (id != null) {
                    writeResult(id, prepareRenameResult(body));
                }
            }
            case "textDocument/rename" -> {
                if (id != null) {
                    writeResult(id, renameResult(body));
                }
            }
            case "textDocument/formatting" -> {
                if (id != null) {
                    writeResult(id, formattingResult(body));
                }
            }
            case "completionItem/resolve" -> {
                if (id != null) {
                    // Pass-through: items are already complete
                    int params = body.indexOf("\"params\"");
                    writeResult(id, params < 0 ? "{}" : extractObject(body, params));
                }
            }
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
                + "\"textDocumentSync\":1,"
                + "\"completionProvider\":{"
                + "\"triggerCharacters\":[\".\"],"
                + "\"resolveProvider\":false"
                + "},"
                + "\"signatureHelpProvider\":{"
                + "\"triggerCharacters\":[\"(\",\",\"],"
                + "\"retriggerCharacters\":[\",\"]"
                + "},"
                + "\"definitionProvider\":true,"
                + "\"referencesProvider\":true,"
                + "\"hoverProvider\":true,"
                + "\"documentSymbolProvider\":true,"
                + "\"workspaceSymbolProvider\":true,"
                + "\"codeActionProvider\":{"
                + "\"codeActionKinds\":[\"quickfix\"]"
                + "},"
                + "\"renameProvider\":{\"prepareProvider\":true},"
                + "\"documentFormattingProvider\":true"
                + "},"
                + "\"serverInfo\":{\"name\":\"jnative-lsp\",\"version\":\"" + io.jnative.Main.VERSION + "\"}"
                + "}";
    }

    private void applyInitOptions(String body) {
        int indent = extractNestedInt(body, "initializationOptions", "formatIndentSize", -1);
        if (indent > 0 && indent < 16) {
            formatIndent = indent;
        }
        if (body.contains("\"formatInsertSpaces\":false")
                || body.contains("\"formatInsertSpaces\": false")) {
            formatInsertSpaces = false;
        } else if (body.contains("\"formatInsertSpaces\":true")
                || body.contains("\"formatInsertSpaces\": true")) {
            formatInsertSpaces = true;
        }
    }

    private void onDidChangeConfiguration(String body) {
        applyInitOptions(body);
        Integer indent = extractNestedIntOptional(body, "jnative", "formatIndentSize");
        if (indent == null) {
            int v = extractNestedInt(body, "settings", "formatIndentSize", -1);
            if (v > 0 && v < 16) {
                formatIndent = v;
            }
        } else if (indent > 0 && indent < 16) {
            formatIndent = indent;
        }
        if (body.contains("\"formatInsertSpaces\":false")
                || body.contains("\"formatInsertSpaces\": false")) {
            formatInsertSpaces = false;
        } else if (body.contains("\"formatInsertSpaces\":true")
                || body.contains("\"formatInsertSpaces\": true")) {
            formatInsertSpaces = true;
        }
    }

    private static Integer extractNestedIntOptional(String json, String obj, String key) {
        int v = extractNestedInt(json, obj, key, Integer.MIN_VALUE);
        return v == Integer.MIN_VALUE ? null : v;
    }

    private String prepareRenameResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "null";
        }
        try {
            RenameService.Prepare p = renamer.prepare(
                    uriToPath(uri), doc.text, line, col, doc.version, openOverlays());
            if (!p.ok()) {
                showMessage(1, "重命名: " + p.error());
                return "null";
            }
            return "{\"range\":{\"start\":{\"line\":" + p.startLine0()
                    + ",\"character\":" + p.startCol0()
                    + "},\"end\":{\"line\":" + p.endLine0()
                    + ",\"character\":" + p.endCol0()
                    + "}},\"placeholder\":" + jsonString(p.placeholder()) + "}";
        } catch (Throwable t) {
            System.err.println("jnative lsp: prepareRename error: " + t.getMessage());
            return "null";
        }
    }

    private String renameResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        String newName = extractString(body, "newName");
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "null";
        }
        try {
            RenameService.RenameResult r = renamer.rename(
                    uriToPath(uri), doc.text, line, col, newName, doc.version, openOverlays());
            if (!r.ok()) {
                showMessage(1, "重命名失败: " + r.error());
                return "null";
            }
            return workspaceEditJson(r.edits());
        } catch (Throwable t) {
            System.err.println("jnative lsp: rename error: " + t.getMessage());
            showMessage(1, "重命名失败: " + t.getMessage());
            return "null";
        }
    }

    private String formattingResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "[]";
        }
        int tabSize = extractNestedInt(body, "options", "tabSize", formatIndent);
        // insertSpaces may be boolean — treat missing as configured default
        boolean insertSpaces = formatInsertSpaces;
        if (body.contains("\"insertSpaces\":false") || body.contains("\"insertSpaces\": false")) {
            insertSpaces = false;
        } else if (body.contains("\"insertSpaces\":true") || body.contains("\"insertSpaces\": true")) {
            insertSpaces = true;
        }
        try {
            var formatter = new io.jnative.format.SourceFormatter(
                    new io.jnative.format.SourceFormatter.Options(
                            tabSize > 0 ? tabSize : 4, insertSpaces));
            var result = formatter.formatDocument(uriToPath(uri), doc.text);
            if (!result.ok()) {
                showMessage(1, "格式化失败: " + result.error());
                return "[]";
            }
            if (result.text().equals(doc.text)) {
                return "[]";
            }
            String[] oldLines = doc.text.replace("\r\n", "\n").split("\n", -1);
            int endLine = Math.max(0, oldLines.length - 1);
            int endCol = oldLines.length == 0 ? 0 : oldLines[endLine].length();
            // Full-document replace (preserves client undo)
            return "[{\"range\":{\"start\":{\"line\":0,\"character\":0},"
                    + "\"end\":{\"line\":" + endLine + ",\"character\":" + endCol + "}},"
                    + "\"newText\":" + jsonString(result.text()) + "}]";
        } catch (Throwable t) {
            System.err.println("jnative lsp: formatting error: " + t.getMessage());
            showMessage(1, "格式化失败: " + t.getMessage());
            return "[]";
        }
    }

    private String workspaceEditJson(List<RenameService.Edit> edits) {
        Map<String, StringBuilder> byUri = new HashMap<>();
        for (RenameService.Edit e : edits) {
            String u = pathToUri(e.file());
            StringBuilder arr = byUri.computeIfAbsent(u, k -> new StringBuilder("["));
            if (arr.length() > 1) {
                arr.append(',');
            }
            arr.append("{\"range\":{\"start\":{\"line\":").append(e.startLine0())
                    .append(",\"character\":").append(e.startCol0())
                    .append("},\"end\":{\"line\":").append(e.endLine0())
                    .append(",\"character\":").append(e.endCol0())
                    .append("}},\"newText\":").append(jsonString(e.newText())).append('}');
        }
        StringBuilder sb = new StringBuilder("{\"changes\":{");
        boolean first = true;
        for (Map.Entry<String, StringBuilder> en : byUri.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(jsonString(en.getKey())).append(':').append(en.getValue()).append(']');
        }
        sb.append("}}");
        return sb.toString();
    }

    private String completionResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            System.err.println("jnative lsp: completion — no open doc for " + uri);
            return "[]";
        }
        String path = uriToPath(uri);
        List<CompletionEngine.Item> items;
        try {
            items = completions.complete(path, doc.text, line, col);
        } catch (Throwable t) {
            System.err.println("jnative lsp: completion error: " + t.getMessage());
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (CompletionEngine.Item it : items) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            boolean snippet = it.insertText() != null && it.insertText().contains("${");
            sb.append('{')
                    .append("\"label\":").append(jsonString(it.label())).append(',')
                    .append("\"kind\":").append(it.kind()).append(',')
                    .append("\"detail\":").append(jsonString(it.detail())).append(',')
                    .append("\"insertText\":").append(jsonString(it.insertText())).append(',')
                    .append("\"insertTextFormat\":").append(snippet ? 2 : 1).append(',')
                    .append("\"filterText\":").append(jsonString(
                            it.filterText() != null ? it.filterText() : it.label()))
                    .append('}');
        }
        sb.append(']');
        System.err.println("jnative lsp: completion items=" + items.size()
                + " at " + line + ":" + col);
        return sb.toString();
    }

    private String signatureHelpResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "null";
        }
        CompletionEngine.Signature sig;
        try {
            sig = completions.signatureHelp(uriToPath(uri), doc.text, line, col);
        } catch (Throwable t) {
            System.err.println("jnative lsp: signatureHelp error: " + t.getMessage());
            return "null";
        }
        if (sig == null) {
            return "null";
        }
        StringBuilder params = new StringBuilder();
        params.append('[');
        for (int i = 0; i < sig.parameters().size(); i++) {
            if (i > 0) {
                params.append(',');
            }
            params.append("{\"label\":").append(jsonString(sig.parameters().get(i))).append('}');
        }
        params.append(']');
        return "{\"signatures\":[{\"label\":" + jsonString(sig.label())
                + ",\"parameters\":" + params + "}],"
                + "\"activeSignature\":0,"
                + "\"activeParameter\":" + sig.activeParameter() + "}";
    }

    private static String extractObject(String json, int fromKey) {
        int brace = json.indexOf('{', fromKey);
        if (brace < 0) {
            return "{}";
        }
        int depth = 0;
        for (int i = brace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(brace, i + 1);
                }
            }
        }
        return "{}";
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
        String path = uriToPath(uri);
        docs.remove(uri);
        diagnoseGen.remove(uri);
        ide.invalidate(path);
        publishDiagnostics(uri, List.of());
    }

    private void onWatchedFiles(String body) {
        // Invalidate project caches when files appear/disappear/change on disk
        // (create/delete/rename, external tools, manifest/lock updates).
        int idx = 0;
        int invalidated = 0;
        while (true) {
            int u = body.indexOf("\"uri\"", idx);
            if (u < 0) {
                break;
            }
            String uri = extractString(body.substring(u), "uri");
            idx = u + 5;
            if (uri == null) {
                continue;
            }
            String path = uriToPath(uri);
            ide.invalidate(path);
            invalidated++;
        }
        if (invalidated > 0) {
            LspLog.info("watched files changed → invalidated " + invalidated + " path(s)");
        }
    }

    private void scheduleAnalyze(String uri, String text, int version) {
        diagnoseGen.put(uri, version);
        workers.execute(() -> {
            try {
                Thread.sleep(350); // coalesce keystrokes; completion uses separate fast path
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Integer latest = diagnoseGen.get(uri);
            if (latest == null || latest != version) {
                return; // superseded
            }
            DocState cur = docs.get(uri);
            if (cur == null || cur.version != version) {
                return;
            }
            String path = uriToPath(uri);
            // Do not invalidate here — cache key includes overlay hashes + disk fingerprint
            Compilation c = ide.analyzeCached(path, text, version, openOverlays());
            latest = diagnoseGen.get(uri);
            if (latest == null || latest != version) {
                return;
            }
            cur = docs.get(uri);
            if (cur == null || cur.version != version) {
                return;
            }
            List<Diagnostic> forFile = c.diagnostics().stream()
                    .filter(d -> d.location() != null && pathEndsWith(d.location().file(), path))
                    .toList();
            publishDiagnostics(uri, forFile);
        });
    }

    private Map<String, String> openOverlays() {
        Map<String, String> overlays = new HashMap<>();
        for (DocState d : docs.values()) {
            overlays.put(uriToPath(d.uri), d.text);
        }
        return overlays;
    }

    private static boolean pathEndsWith(String file, String path) {
        if (file == null || path == null) {
            return false;
        }
        return file.equals(path) || file.endsWith(path) || path.endsWith(file)
                || Path.of(file).toAbsolutePath().normalize()
                .equals(Path.of(path).toAbsolutePath().normalize());
    }

    private String definitionResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "null";
        }
        String path = uriToPath(uri);
        try {
            List<IdeServices.Location> locs = ide.definition(
                    path, doc.text, line, col, doc.version, openOverlays());
            if (locs.size() == 1 && locs.get(0).file().isEmpty() && locs.get(0).message() != null) {
                showMessage(1, locs.get(0).message());
                return "null";
            }
            return locationsJson(locs);
        } catch (Throwable t) {
            System.err.println("jnative lsp: definition error: " + t.getMessage());
            return "null";
        }
    }

    private String referencesResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "[]";
        }
        try {
            List<IdeServices.Location> locs = ide.references(
                    uriToPath(uri), doc.text, line, col, doc.version, openOverlays());
            return locationsJson(locs);
        } catch (Throwable t) {
            System.err.println("jnative lsp: references error: " + t.getMessage());
            return "[]";
        }
    }

    private String hoverResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        int line = extractNestedInt(body, "position", "line", 0);
        int col = extractNestedInt(body, "position", "character", 0);
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "null";
        }
        try {
            IdeServices.Hover h = ide.hover(
                    uriToPath(uri), doc.text, line, col, doc.version, openOverlays());
            if (h == null || h.markdown() == null || h.markdown().isBlank()) {
                return "null";
            }
            return "{\"contents\":{\"kind\":\"markdown\",\"value\":"
                    + jsonString(h.markdown()) + "}}";
        } catch (Throwable t) {
            System.err.println("jnative lsp: hover error: " + t.getMessage());
            return "null";
        }
    }

    private String documentSymbolResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "[]";
        }
        try {
            List<IdeServices.DocSymbol> syms = ide.documentSymbols(
                    uriToPath(uri), doc.text, doc.version, openOverlays());
            return docSymbolsJson(syms);
        } catch (Throwable t) {
            System.err.println("jnative lsp: documentSymbol error: " + t.getMessage());
            return "[]";
        }
    }

    private String workspaceSymbolResult(String body) {
        String query = extractString(body, "query");
        String hint = null;
        String hintText = "";
        if (!docs.isEmpty()) {
            DocState first = docs.values().iterator().next();
            hint = uriToPath(first.uri);
            hintText = first.text;
        }
        try {
            // Ensure model exists even if query-only
            if (hint != null) {
                ide.analyzeCached(hint, hintText, 0, openOverlays());
            }
            List<IdeServices.WorkspaceSymbol> syms = ide.workspaceSymbols(query, openOverlays(), hint);
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (IdeServices.WorkspaceSymbol s : syms) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                String fileUri = pathToUri(s.file());
                int l = Math.max(0, s.line1() - 1);
                int c = Math.max(0, s.col1() - 1);
                sb.append("{\"name\":").append(jsonString(s.name()))
                        .append(",\"kind\":").append(s.kind())
                        .append(",\"containerName\":").append(jsonString(s.container()))
                        .append(",\"location\":{\"uri\":").append(jsonString(fileUri))
                        .append(",\"range\":{\"start\":{\"line\":").append(l)
                        .append(",\"character\":").append(c)
                        .append("},\"end\":{\"line\":").append(l)
                        .append(",\"character\":").append(c + 1).append("}}}}");
            }
            sb.append(']');
            return sb.toString();
        } catch (Throwable t) {
            System.err.println("jnative lsp: workspace/symbol error: " + t.getMessage());
            return "[]";
        }
    }

    private String codeActionResult(String body) {
        String uri = extractNestedString(body, "textDocument", "uri");
        DocState doc = uri == null ? null : docs.get(uri);
        if (doc == null) {
            return "[]";
        }
        String path = uriToPath(uri);
        try {
            int atLine = -1;
            int ri = body.indexOf("\"range\"");
            if (ri >= 0) {
                int li = body.indexOf("\"line\":", ri);
                if (li >= 0) {
                    int k = li + 7;
                    while (k < body.length() && body.charAt(k) == ' ') {
                        k++;
                    }
                    int e = k;
                    while (e < body.length() && Character.isDigit(body.charAt(e))) {
                        e++;
                    }
                    if (e > k) {
                        atLine = Integer.parseInt(body.substring(k, e));
                    }
                }
            }
            List<IdeServices.CodeAction> actions = ide.codeActions(
                    path, doc.text, doc.version, openOverlays(), List.of(), atLine);
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (IdeServices.CodeAction a : actions) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"title\":").append(jsonString(a.title()))
                        .append(",\"kind\":").append(jsonString(a.kind()))
                        .append(",\"edit\":{\"changes\":{");
                // Group edits by URI
                Map<String, StringBuilder> byUri = new HashMap<>();
                for (IdeServices.TextEdit e : a.edits()) {
                    String eu = pathToUri(e.file());
                    StringBuilder arr = byUri.computeIfAbsent(eu, k -> new StringBuilder("["));
                    if (arr.length() > 1) {
                        arr.append(',');
                    }
                    arr.append("{\"range\":{\"start\":{\"line\":").append(e.startLine0())
                            .append(",\"character\":").append(e.startCol0())
                            .append("},\"end\":{\"line\":").append(e.endLine0())
                            .append(",\"character\":").append(e.endCol0())
                            .append("}},\"newText\":").append(jsonString(e.newText())).append('}');
                }
                boolean firstUri = true;
                for (Map.Entry<String, StringBuilder> en : byUri.entrySet()) {
                    if (!firstUri) {
                        sb.append(',');
                    }
                    firstUri = false;
                    sb.append(jsonString(en.getKey())).append(':').append(en.getValue()).append(']');
                }
                sb.append("}}}");
            }
            sb.append(']');
            return sb.toString();
        } catch (Throwable t) {
            System.err.println("jnative lsp: codeAction error: " + t.getMessage());
            return "[]";
        }
    }

    private String locationsJson(List<IdeServices.Location> locs) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (IdeServices.Location loc : locs) {
            if (loc.file() == null || loc.file().isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            int l = Math.max(0, loc.line1() - 1);
            int c = Math.max(0, loc.col1() - 1);
            sb.append("{\"uri\":").append(jsonString(pathToUri(loc.file())))
                    .append(",\"range\":{\"start\":{\"line\":").append(l)
                    .append(",\"character\":").append(c)
                    .append("},\"end\":{\"line\":").append(l)
                    .append(",\"character\":").append(c + 1).append("}}}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String docSymbolsJson(List<IdeServices.DocSymbol> syms) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (IdeServices.DocSymbol s : syms) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(docSymbolJson(s));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String docSymbolJson(IdeServices.DocSymbol s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":").append(jsonString(s.name()))
                .append(",\"kind\":").append(s.kind())
                .append(",\"range\":{\"start\":{\"line\":").append(s.startLine0())
                .append(",\"character\":").append(s.startCol0())
                .append("},\"end\":{\"line\":").append(s.endLine0())
                .append(",\"character\":").append(s.endCol0()).append("}},")
                .append("\"selectionRange\":{\"start\":{\"line\":").append(s.startLine0())
                .append(",\"character\":").append(s.startCol0())
                .append("},\"end\":{\"line\":").append(s.endLine0())
                .append(",\"character\":").append(s.endCol0()).append("}},")
                .append("\"children\":").append(docSymbolsJson(s.children())).append('}');
        return sb.toString();
    }

    private static String pathToUri(String path) {
        if (path == null) {
            return "file:///Memory.java";
        }
        if (path.startsWith("file:")) {
            return path;
        }
        Path p = Path.of(path).toAbsolutePath().normalize();
        return p.toUri().toString();
    }

    private void showMessage(int type, String message) {
        try {
            writeRaw("{\"jsonrpc\":\"2.0\",\"method\":\"window/showMessage\",\"params\":{"
                    + "\"type\":" + type + ",\"message\":" + jsonString(message) + "}}");
        } catch (IOException e) {
            System.err.println("jnative lsp: showMessage failed: " + e.getMessage());
        }
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
        if (asciiOnly) {
            StringBuilder esc = new StringBuilder(json.length() + 16);
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c > 126) {
                    esc.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                } else {
                    esc.append(c);
                }
            }
            json = esc.toString();
        }
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
