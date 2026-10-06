package io.jnative.dev;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Cross-platform file watcher (NIO WatchService: inotify / kqueue / ReadDirectoryChangesW).
 * Debounces rapid saves and ignores build/cache trees.
 */
public final class FileWatcher implements AutoCloseable {
    public record Change(Path path, Kind kind) {
        public enum Kind { CREATE, MODIFY, DELETE, OVERFLOW }
    }

    private final Path root;
    private final Set<String> ignoreNames;
    private final long debounceMs;
    private final List<Consumer<List<Change>>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private WatchService watchService;
    private Thread thread;

    public FileWatcher(Path root) {
        this(root, 80);
    }

    public FileWatcher(Path root, long debounceMs) {
        this.root = root.toAbsolutePath().normalize();
        this.debounceMs = Math.max(20, debounceMs);
        this.ignoreNames = Set.of(
                ".jnative", "build", "dist", ".git", ".idea", ".vscode",
                "target", "node_modules", ".DS_Store", "*.swp", "*.tmp"
        );
    }

    public void addListener(Consumer<List<Change>> listener) {
        listeners.add(listener);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        watchService = FileSystems.getDefault().newWatchService();
        registerTree(root);
        thread = new Thread(this::loop, "jnative-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void loop() {
        List<Change> pending = new ArrayList<>();
        long lastEvent = 0;
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(debounceMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key != null) {
                    Path dir = (Path) key.watchable();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            pending.add(new Change(dir, Change.Kind.OVERFLOW));
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path name = ev.context();
                        if (name == null) {
                            continue;
                        }
                        Path full = dir.resolve(name);
                        if (shouldIgnore(full)) {
                            continue;
                        }
                        Change.Kind ck = Change.Kind.MODIFY;
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            ck = Change.Kind.CREATE;
                            if (Files.isDirectory(full)) {
                                try {
                                    registerTree(full);
                                } catch (IOException ignored) {
                                }
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            ck = Change.Kind.DELETE;
                        }
                        pending.add(new Change(full, ck));
                    }
                    key.reset();
                    lastEvent = System.currentTimeMillis();
                }
                if (!pending.isEmpty() && System.currentTimeMillis() - lastEvent >= debounceMs) {
                    List<Change> batch = coalesce(pending);
                    pending.clear();
                    for (Consumer<List<Change>> l : listeners) {
                        try {
                            l.accept(batch);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Change> coalesce(List<Change> pending) {
        // Last event per path wins (atomic save: temp create + rename → one MODIFY)
        java.util.LinkedHashMap<String, Change> map = new java.util.LinkedHashMap<>();
        for (Change c : pending) {
            map.put(c.path().toString(), c);
        }
        return new ArrayList<>(map.values());
    }

    private boolean shouldIgnore(Path path) {
        Path rel;
        try {
            rel = root.relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return true;
        }
        for (Path part : rel) {
            String n = part.toString();
            if (ignoreNames.contains(n)) {
                return true;
            }
            String lower = n.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".swp") || lower.endsWith("~") || lower.startsWith(".#")
                    || lower.endsWith(".tmp") || lower.endsWith(".class")) {
                return true;
            }
        }
        return false;
    }

    private void registerTree(Path start) throws IOException {
        if (!Files.isDirectory(start)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldIgnore(dir) && !dir.equals(root)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void close() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
    }
}
