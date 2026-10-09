package io.jnative.dev;

import io.jnative.build.BuildTargetSpec;
import io.jnative.build.JNativeProject;
import io.jnative.incremental.BuildResult;
import io.jnative.incremental.CompilerSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Orchestrates watcher, incremental builds, native server process, and browser reload.
 * @author 张晓达
 */
public final class DevCoordinator implements AutoCloseable {
    private final Path projectRoot;
    private final int browserPort;
    private final int serverPort;
    private final CompilerSession session;
    private FileWatcher watcher;
    private BrowserDevServer browserServer;
    private Process serverProcess;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong buildGen = new AtomicLong();
    private final AtomicReference<BuildResult> lastGood = new AtomicReference<>();
    private Thread shutdownHook;

    public DevCoordinator(Path projectRoot, int browserPort, int serverPort) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.browserPort = browserPort;
        this.serverPort = serverPort;
        this.session = new CompilerSession(this.projectRoot);
        this.session.setOptLevel(1); // -Odev
    }

    public CompilerSession session() {
        return session;
    }

    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        System.out.println("JNative Dev Server\n");
        System.out.println("Server:   http://localhost:" + serverPort);
        System.out.println("Browser:  http://localhost:" + browserPort);
        System.out.println();
        System.out.println("Watching...\n");

        BuildResult initial = session.build();
        printBuild(initial);
        if (initial.success()) {
            lastGood.set(initial);
            System.out.println("✓ server ready");
            System.out.println("✓ browser ready");
        } else {
            System.out.println("[build] initial build had errors; last-known-good not set");
            for (String d : initial.diagnostics()) {
                System.out.println("[build] " + d);
            }
        }

        Path browserAssets = resolveBrowserAssets();
        Files.createDirectories(browserAssets);
        ensureIndex(browserAssets);
        browserServer = new BrowserDevServer(browserAssets, browserPort);
        try {
            browserServer.start();
        } catch (IOException e) {
            throw e;
        }

        maybeStartServer(initial);

        watcher = new FileWatcher(projectRoot, 100);
        watcher.addListener(this::onChanges);
        watcher.start();

        shutdownHook = new Thread(this::shutdownQuiet, "jnative-dev-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Block until Ctrl+C
        while (running.get()) {
            Thread.sleep(200);
        }
    }

    private void onChanges(List<FileWatcher.Change> changes) {
        long gen = buildGen.incrementAndGet();
        session.cancelStale(session.currentGeneration());

        List<String> javaFiles = new ArrayList<>();
        List<String> cssFiles = new ArrayList<>();
        List<String> otherAssets = new ArrayList<>();
        for (FileWatcher.Change c : changes) {
            Path abs = c.path();
            String rel;
            try {
                rel = projectRoot.relativize(abs).toString().replace('\\', '/');
            } catch (IllegalArgumentException e) {
                continue;
            }
            String lower = rel.toLowerCase();
            if (lower.endsWith(".java")) {
                javaFiles.add(rel);
            } else if (lower.endsWith(".css")) {
                cssFiles.add(rel);
            } else if (lower.endsWith(".html") || lower.endsWith(".js") || lower.endsWith(".png")
                    || lower.endsWith(".svg") || lower.endsWith(".jpg")) {
                otherAssets.add(rel);
            }
        }

        System.out.println("[build] change detected");
        try {
            if (!cssFiles.isEmpty() && javaFiles.isEmpty()) {
                for (String css : cssFiles) {
                    copyAsset(css);
                }
                BuildResult r = session.build(cssFiles, true);
                printBuild(r);
                browserServer.broadcast(ReloadProtocol.RELOAD_CSS, String.join(",", cssFiles));
                System.out.println("[browser] CSS reload");
                return;
            }
            if (!otherAssets.isEmpty() && javaFiles.isEmpty() && cssFiles.isEmpty()) {
                for (String a : otherAssets) {
                    copyAsset(a);
                }
                browserServer.broadcast(ReloadProtocol.RELOAD_PAGE, "asset");
                System.out.println("[browser] asset reload");
                return;
            }
            if (javaFiles.isEmpty()) {
                return;
            }
            for (String f : javaFiles) {
                System.out.println("[build] → " + f);
            }
            BuildResult r = session.build(javaFiles, false);
            if (gen != buildGen.get()) {
                System.out.println("[build] stale generation discarded");
                return;
            }
            printBuild(r);
            if (!r.success()) {
                String diag = String.join("\n", r.diagnostics());
                browserServer.broadcast(ReloadProtocol.BUILD_ERROR, diag);
                System.out.println("[build] rejected; last-known-good preserved");
                return;
            }
            lastGood.set(r);
            browserServer.broadcast(ReloadProtocol.BUILD_OK, "ok");

            boolean serverRestart = !r.restartedTargets().isEmpty()
                    || r.linkedTargets().stream().anyMatch(t -> t.contains("server"));
            boolean browserReload = !r.reloadedTargets().isEmpty()
                    || r.linkedTargets().stream().anyMatch(t -> t.contains("browser"));

            if (serverRestart) {
                restartServer();
                System.out.println("[server] restarted");
            } else {
                System.out.println("[server] restart: NO");
            }
            if (browserReload || !serverRestart) {
                // WASM full page reload acceptable in 0.29
                if (browserReload || javaFiles.stream().anyMatch(f -> f.contains("browser"))) {
                    Path assets = resolveBrowserAssets();
                    syncBrowserDist(assets);
                    browserServer.broadcast(ReloadProtocol.RELOAD_PAGE, "wasm");
                    System.out.println("[browser] reload");
                }
            }
        } catch (Exception e) {
            System.out.println("[build] error: " + e.getMessage());
            if (browserServer != null) {
                browserServer.broadcast(ReloadProtocol.BUILD_ERROR, e.getMessage());
            }
        }
    }

    private void maybeStartServer(BuildResult initial) {
        Path bin = findServerBinary();
        if (bin != null && Files.isRegularFile(bin)) {
            try {
                startServerProcess(bin);
            } catch (IOException e) {
                System.out.println("[server] start failed: " + e.getMessage());
            }
        } else {
            System.out.println("[server] no binary yet (compile server target to enable process)");
        }
    }

    private Path findServerBinary() {
        JNativeProject p = session.project();
        for (BuildTargetSpec t : p.targets()) {
            if (t.isBrowser()) {
                continue;
            }
            if ("server".equals(t.name()) || t.name().contains("server")
                    || (p.module(t.module()) != null && "server".equals(p.module(t.module()).kind()))) {
                Path out = projectRoot.resolve(t.output());
                if (Files.isRegularFile(out)) {
                    return out;
                }
            }
        }
        Path fallback = projectRoot.resolve(".jnative/build/server");
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    private void restartServer() throws IOException, InterruptedException {
        stopServer(1500);
        Path bin = findServerBinary();
        if (bin == null) {
            System.out.println("[server] no binary to restart");
            return;
        }
        startServerProcess(bin);
    }

    private void startServerProcess(Path bin) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        Thread pump = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("[server] " + line);
                }
            } catch (IOException ignored) {
            }
            int code = serverProcess.exitValue();
            System.out.println("[server] exited code=" + code);
        }, "jnative-server-out");
        pump.setDaemon(true);
        pump.start();
    }

    private void stopServer(long graceMs) throws InterruptedException {
        if (serverProcess == null || !serverProcess.isAlive()) {
            return;
        }
        serverProcess.destroy();
        boolean done = serverProcess.waitFor(graceMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!done) {
            serverProcess.destroyForcibly();
        }
        serverProcess = null;
    }

    private Path resolveBrowserAssets() {
        JNativeProject p = session.project();
        for (BuildTargetSpec t : p.targets()) {
            if (t.isBrowser()) {
                Path out = projectRoot.resolve(t.output());
                return out;
            }
        }
        Path dist = projectRoot.resolve("browser/dist");
        if (Files.isDirectory(dist)) {
            return dist;
        }
        return projectRoot.resolve(".jnative/build/browser");
    }

    private void syncBrowserDist(Path assets) throws IOException {
        // Prefer compiled output; also copy public CSS next to it
        Path publicCss = projectRoot.resolve("browser/public");
        if (Files.isDirectory(publicCss)) {
            Files.walk(publicCss).filter(Files::isRegularFile).forEach(src -> {
                try {
                    Path dest = assets.resolve(publicCss.relativize(src).toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private void copyAsset(String rel) throws IOException {
        Path src = projectRoot.resolve(rel);
        if (!Files.isRegularFile(src)) {
            return;
        }
        Path assets = resolveBrowserAssets();
        String name = Path.of(rel).getFileName().toString();
        Path dest = assets.resolve(name);
        Files.createDirectories(assets);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        // also from browser/public
        if (rel.contains("public/") || rel.endsWith(".css")) {
            Files.copy(src, assets.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureIndex(Path assets) throws IOException {
        Path index = assets.resolve("index.html");
        if (!Files.exists(index)) {
            Files.writeString(index, """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"><title>JNative Dev</title>
                    <link rel="stylesheet" href="app.css">
                    </head><body>
                    <h1>JNative Dev</h1>
                    <p>Waiting for browser target...</p>
                    <script src="app.js"></script>
                    </body></html>
                    """);
        }
        Path css = assets.resolve("app.css");
        if (!Files.exists(css)) {
            Path pub = projectRoot.resolve("browser/public/app.css");
            if (Files.isRegularFile(pub)) {
                Files.copy(pub, css, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.writeString(css, "body{font-family:system-ui;margin:2rem}\n");
            }
        }
    }

    private static void printBuild(BuildResult r) {
        System.out.println("[build] " + r.summary().lines().collect(Collectors.joining("\n[build] ")));
        if (sessionExplain(r)) {
            for (String e : r.explain()) {
                System.out.println("[build] " + e.replace("\n", "\n[build] "));
            }
        }
    }

    private static boolean sessionExplain(BuildResult r) {
        return !r.explain().isEmpty() && r.changed().size() <= 20;
    }

    private void shutdownQuiet() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() throws Exception {
        if (!running.compareAndSet(true, false)) {
            // still clean resources
        }
        System.out.println("\n[build] shutting down...");
        if (watcher != null) {
            watcher.close();
        }
        stopServer(1000);
        if (browserServer != null) {
            browserServer.close();
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
        System.out.println("[build] ports released");
    }
}
