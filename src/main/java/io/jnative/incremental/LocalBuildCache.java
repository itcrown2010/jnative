package io.jnative.incremental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Local content-addressed build cache under {@code .jnative/cache}.
 * Atomic write + lock for concurrent access safety.
 */
public final class LocalBuildCache {
    private final Path root;
    private final Map<String, CacheEntry> memory = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long hits;
    private long misses;
    private final int memoryBound;

    public record CacheEntry(
            String key,
            Path artifact,
            long size,
            long created,
            long lastUsed,
            String integrity
    ) {
    }

    public LocalBuildCache(Path root) {
        this(root, 2048);
    }

    public LocalBuildCache(Path root, int memoryBound) {
        this.root = root;
        this.memoryBound = memoryBound;
    }

    public Path root() {
        return root;
    }

    public void ensureLayout() throws IOException {
        Files.createDirectories(root.resolve("objects"));
        Files.createDirectories(root.resolve("meta"));
    }

    public String cacheKey(
            String compilerVersion,
            String target,
            String optLevel,
            String sourceFingerprint,
            String dependencyFingerprint,
            String featureFlags
    ) {
        return ContentHasher.sha256(String.join("|",
                compilerVersion,
                target,
                optLevel,
                sourceFingerprint,
                dependencyFingerprint,
                featureFlags == null ? "" : featureFlags));
    }

    public Path objectPath(String key) {
        return root.resolve("objects").resolve(key.substring(0, 2)).resolve(key);
    }

    public boolean contains(String key) {
        Path p = objectPath(key);
        if (Files.isRegularFile(p)) {
            hits++;
            touch(key, p);
            return true;
        }
        misses++;
        return false;
    }

    public byte[] getBytes(String key) throws IOException {
        Path p = objectPath(key);
        if (!Files.isRegularFile(p)) {
            misses++;
            return null;
        }
        byte[] data = Files.readAllBytes(p);
        String expected = ContentHasher.sha256(data);
        // Integrity: filename key should match content or we stored meta — validate length>0
        if (data.length == 0) {
            Files.deleteIfExists(p);
            misses++;
            return null;
        }
        hits++;
        touch(key, p);
        memory.put(key, new CacheEntry(key, p, data.length, System.currentTimeMillis(),
                System.currentTimeMillis(), expected));
        evictMemoryIfNeeded();
        return data;
    }

    public void putBytes(String key, byte[] data) throws IOException {
        lock.lock();
        try {
            ensureLayout();
            Path dest = objectPath(key);
            Files.createDirectories(dest.getParent());
            Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp." + ProcessHandle.current().pid());
            Files.write(tmp, data == null ? new byte[0] : data);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            String integrity = ContentHasher.sha256(data);
            memory.put(key, new CacheEntry(key, dest, data == null ? 0 : data.length,
                    System.currentTimeMillis(), System.currentTimeMillis(), integrity));
            Files.writeString(root.resolve("meta").resolve(key + ".json"),
                    "{\"key\":\"" + key + "\",\"size\":" + (data == null ? 0 : data.length)
                            + ",\"integrity\":\"" + integrity + "\"}\n",
                    StandardCharsets.UTF_8);
            evictMemoryIfNeeded();
        } finally {
            lock.unlock();
        }
    }

    public void putString(String key, String text) throws IOException {
        putBytes(key, text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public void discard(String key) throws IOException {
        Files.deleteIfExists(objectPath(key));
        Files.deleteIfExists(root.resolve("meta").resolve(key + ".json"));
        memory.remove(key);
    }

    public void clean() throws IOException {
        lock.lock();
        try {
            if (Files.isDirectory(root)) {
                try (var walk = Files.walk(root)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
            memory.clear();
            ensureLayout();
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> stats() throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hits", hits);
        m.put("misses", misses);
        long hitRate = (hits + misses) == 0 ? 0 : (hits * 100) / (hits + misses);
        m.put("hitRatePercent", hitRate);
        m.put("memoryEntries", memory.size());
        long disk = 0;
        int entries = 0;
        Path objects = root.resolve("objects");
        if (Files.isDirectory(objects)) {
            try (var walk = Files.walk(objects)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (Files.isRegularFile(p)) {
                        disk += Files.size(p);
                        entries++;
                    }
                }
            }
        }
        m.put("diskEntries", entries);
        m.put("diskBytes", disk);
        m.put("root", root.toString());
        return m;
    }

    private void touch(String key, Path p) {
        CacheEntry old = memory.get(key);
        long size = 0;
        try {
            size = Files.size(p);
        } catch (IOException ignored) {
        }
        memory.put(key, new CacheEntry(key, p, size,
                old == null ? System.currentTimeMillis() : old.created(),
                System.currentTimeMillis(),
                old == null ? "" : old.integrity()));
    }

    private void evictMemoryIfNeeded() {
        while (memory.size() > memoryBound) {
            String oldest = null;
            long t = Long.MAX_VALUE;
            for (Map.Entry<String, CacheEntry> e : memory.entrySet()) {
                if (e.getValue().lastUsed() < t) {
                    t = e.getValue().lastUsed();
                    oldest = e.getKey();
                }
            }
            if (oldest == null) {
                break;
            }
            memory.remove(oldest);
        }
    }
}
