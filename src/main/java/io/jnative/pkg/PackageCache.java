package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;
import io.jnative.incremental.LocalBuildCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Content-addressed package cache shared across projects (under .jnative/pkg-cache or global).
 */
public final class PackageCache {
    private final Path root;
    private final LocalBuildCache objects;
    private final Map<String, Path> extracted = new ConcurrentHashMap<>();

    public PackageCache(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.objects = new LocalBuildCache(this.root.resolve("objects"));
        this.objects.ensureLayout();
        Files.createDirectories(this.root.resolve("extracted"));
        Files.createDirectories(this.root.resolve("meta"));
    }

    public Path root() {
        return root;
    }

    public boolean has(String contentHash) {
        return objects.contains(contentHash) || Files.isDirectory(extractedPath(contentHash));
    }

    public void putArchive(String contentHash, byte[] archive) throws IOException {
        String expected = ContentHasher.sha256(archive);
        if (!expected.equals(contentHash)) {
            throw new IOException("SECURITY ERROR: package hash mismatch expected="
                    + contentHash + " actual=" + expected);
        }
        objects.putBytes(contentHash, archive);
    }

    public byte[] getArchive(String contentHash) throws IOException {
        byte[] data = objects.getBytes(contentHash);
        if (data == null) {
            return null;
        }
        String actual = ContentHasher.sha256(data);
        if (!actual.equals(contentHash)) {
            objects.discard(contentHash);
            throw new IOException("SECURITY ERROR: corrupted cache entry for " + contentHash);
        }
        return data;
    }

    public Path ensureExtracted(String contentHash) throws IOException {
        Path dir = extractedPath(contentHash);
        if (Files.isDirectory(dir) && Files.list(dir).findAny().isPresent()) {
            extracted.put(contentHash, dir);
            return dir;
        }
        byte[] archive = getArchive(contentHash);
        if (archive == null) {
            throw new IOException("package missing from local cache: " + contentHash);
        }
        Path tmp = dir.resolveSibling(dir.getFileName() + ".tmp");
        if (Files.exists(tmp)) {
            deleteTree(tmp);
        }
        PackageArchive.extract(archive, tmp);
        if (Files.exists(dir)) {
            deleteTree(dir);
        }
        Files.move(tmp, dir);
        extracted.put(contentHash, dir);
        return dir;
    }

    public Path extractedPath(String contentHash) {
        String a = contentHash.length() >= 2 ? contentHash.substring(0, 2) : "00";
        return root.resolve("extracted").resolve(a).resolve(contentHash);
    }

    public Map<String, Object> stats() throws IOException {
        return objects.stats();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
