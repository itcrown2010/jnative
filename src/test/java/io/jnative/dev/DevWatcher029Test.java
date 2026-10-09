package io.jnative.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class DevWatcher029Test {

    @TempDir Path tmp;

    @Test
    void watcherDetectsModifyAndIgnoresDotJnative() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.createDirectories(tmp.resolve(".jnative/cache"));
        Path file = src.resolve("A.java");
        Files.writeString(file, "class A {}\n");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<FileWatcher.Change>> seen = new AtomicReference<>(List.of());
        try (FileWatcher w = new FileWatcher(tmp, 50)) {
            w.addListener(batch -> {
                seen.set(new ArrayList<>(batch));
                latch.countDown();
            });
            w.start();
            Thread.sleep(100);
            Files.writeString(file, "class A { int x; }\n");
            Files.writeString(tmp.resolve(".jnative/cache/x"), "noise");
            assertTrue(latch.await(5, TimeUnit.SECONDS), "expected modify event");
            assertTrue(seen.get().stream().anyMatch(c -> c.path().endsWith("A.java")));
            assertTrue(seen.get().stream().noneMatch(c -> c.path().toString().contains(".jnative")));
        }
    }

    @Test
    void reloadProtocolJsonEscapes() {
        String j = ReloadProtocol.json(ReloadProtocol.BUILD_ERROR, "line\n\"x\"");
        assertTrue(j.contains("BUILD_ERROR"));
        assertTrue(j.contains("\\n"));
    }

    @Test
    void browserDevServerServesOverlayAndBroadcasts() throws Exception {
        Path assets = tmp.resolve("assets");
        Files.createDirectories(assets);
        Files.writeString(assets.resolve("index.html"), "<html><body>hi</body></html>");
        int port = freePort();
        try (BrowserDevServer server = new BrowserDevServer(assets, port)) {
            server.start();
            String html = new String(java.net.URI.create("http://127.0.0.1:" + port + "/")
                    .toURL().openStream().readAllBytes());
            assertTrue(html.contains("__jnativeDev") || html.contains("__jnative_overlay")
                    || html.contains("EventSource"));
            server.broadcast(ReloadProtocol.RELOAD_CSS, "app.css");
        }
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
