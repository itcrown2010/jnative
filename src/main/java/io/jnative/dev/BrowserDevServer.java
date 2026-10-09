package io.jnative.dev;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Serves browser assets and pushes reload/diagnostics over SSE.
 * Overlay JS is injected so broken WASM still shows build errors.
 * @author 张晓达
 */
public final class BrowserDevServer implements AutoCloseable {
    private final Path assetRoot;
    private final int port;
    private HttpServer server;
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();

    private static final class SseClient {
        final HttpExchange exchange;
        final OutputStream out;

        SseClient(HttpExchange exchange, OutputStream out) {
            this.exchange = exchange;
            this.out = out;
        }
    }

    public BrowserDevServer(Path assetRoot, int port) {
        this.assetRoot = assetRoot.toAbsolutePath().normalize();
        this.port = port;
    }

    public int port() {
        return port;
    }

    public void start() throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException e) {
            throw new BindException("Port " + port + " already in use");
        }
        server.createContext("/__jnative/events", this::handleSse);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "jnative-browser-dev");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public void broadcast(String type, String payload) {
        String json = ReloadProtocol.json(type, payload);
        String frame = "data: " + json + "\n\n";
        byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        for (SseClient c : clients) {
            try {
                c.out.write(bytes);
                c.out.flush();
            } catch (IOException e) {
                clients.remove(c);
                try {
                    c.exchange.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void handleSse(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/event-stream");
        h.set("Cache-Control", "no-cache");
        h.set("Connection", "keep-alive");
        h.set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();
        out.write("data: {\"type\":\"BUILD_OK\",\"payload\":\"connected\"}\n\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
        clients.add(new SseClient(ex, out));
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String raw = ex.getRequestURI().getPath();
        if (raw == null || raw.isEmpty() || "/".equals(raw)) {
            raw = "/index.html";
        }
        Path file = assetRoot.resolve(raw.substring(1)).normalize();
        if (!file.startsWith(assetRoot) || !Files.isRegularFile(file)) {
            byte[] miss = ("404 " + raw).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, miss.length);
            ex.getResponseBody().write(miss);
            ex.close();
            return;
        }
        byte[] data = Files.readAllBytes(file);
        String name = file.getFileName().toString().toLowerCase();
        String type = "application/octet-stream";
        if (name.endsWith(".html")) {
            type = "text/html; charset=utf-8";
            String html = new String(data, StandardCharsets.UTF_8);
            if (!html.contains("__jnativeDev")) {
                if (html.contains("</body>")) {
                    html = html.replace("</body>", ReloadProtocol.overlayScript() + "</body>");
                } else {
                    html = html + ReloadProtocol.overlayScript();
                }
                data = html.getBytes(StandardCharsets.UTF_8);
            }
        } else if (name.endsWith(".css")) {
            type = "text/css; charset=utf-8";
        } else if (name.endsWith(".js")) {
            type = "application/javascript; charset=utf-8";
        } else if (name.endsWith(".wasm")) {
            type = "application/wasm";
        } else if (name.endsWith(".png")) {
            type = "image/png";
        } else if (name.endsWith(".svg")) {
            type = "image/svg+xml";
        }
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    @Override
    public void close() {
        for (SseClient c : clients) {
            try {
                c.exchange.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
        if (server != null) {
            server.stop(0);
        }
    }
}
