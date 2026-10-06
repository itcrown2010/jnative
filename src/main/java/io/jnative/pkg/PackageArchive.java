package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * .jnpkg archive (zip) with path-traversal / zip-bomb protections.
 */
public final class PackageArchive {
    public static final int MAX_FILES = 10_000;
    public static final long MAX_EXPANDED_BYTES = 512L * 1024L * 1024L; // 512 MiB
    public static final double MAX_COMPRESSION_RATIO = 200.0;

    private PackageArchive() {
    }

    public static byte[] pack(Path packageRoot) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(packageRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = packageRoot.relativize(file).toString().replace('\\', '/');
                if (shouldInclude(name)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = packageRoot.relativize(dir).toString().replace('\\', '/');
                if (name.equals(".git") || name.equals(".jnative") || name.equals("target")
                        || name.equals("build") || name.equals(".idea")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(p -> packageRoot.relativize(p).toString()));
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Path file : files) {
                String entry = packageRoot.relativize(file).toString().replace('\\', '/');
                ZipEntry ze = new ZipEntry(entry);
                ze.setTime(0); // deterministic
                zos.putNextEntry(ze);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    public static String contentHash(byte[] archive) {
        return ContentHasher.sha256(archive);
    }

    public static void extract(byte[] archive, Path dest) throws IOException {
        Files.createDirectories(dest);
        long expanded = 0;
        int files = 0;
        long compressed = archive.length;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                        || name.contains(":\\")) {
                    throw new IOException("SECURITY ERROR: path traversal in archive: " + name);
                }
                if (name.contains("\0")) {
                    throw new IOException("SECURITY ERROR: null byte in archive path");
                }
                Path out = dest.resolve(name).normalize();
                if (!out.startsWith(dest.normalize())) {
                    throw new IOException("SECURITY ERROR: path escape: " + name);
                }
                if (e.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(out);
                    continue;
                }
                // symlink entries — reject
                if (e.getExtra() != null && new String(e.getExtra(), StandardCharsets.UTF_8).contains("symlink")) {
                    throw new IOException("SECURITY ERROR: symlink entry rejected: " + name);
                }
                Files.createDirectories(out.getParent());
                long written = 0;
                try (var os = Files.newOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) >= 0) {
                        written += n;
                        expanded += n;
                        files++;
                        if (files > MAX_FILES) {
                            throw new IOException("SECURITY ERROR: too many files in archive");
                        }
                        if (expanded > MAX_EXPANDED_BYTES) {
                            throw new IOException("SECURITY ERROR: expanded size limit exceeded");
                        }
                        if (compressed > 0 && expanded > compressed * MAX_COMPRESSION_RATIO) {
                            throw new IOException("SECURITY ERROR: compression ratio limit (zip bomb)");
                        }
                        os.write(buf, 0, n);
                    }
                }
                if (written == 0 && !Files.exists(out)) {
                    Files.write(out, new byte[0]);
                }
            }
        }
    }

    public static List<String> listDryRun(Path packageRoot) throws IOException {
        List<String> names = new ArrayList<>();
        Files.walkFileTree(packageRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = packageRoot.relativize(file).toString().replace('\\', '/');
                if (shouldInclude(name)) {
                    names.add(name);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = packageRoot.relativize(dir).toString().replace('\\', '/');
                if (name.equals(".git") || name.equals(".jnative") || name.equals("target")
                        || name.equals(".idea") || name.equals("build")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        names.sort(String::compareTo);
        return names;
    }

    private static boolean shouldInclude(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".env") || lower.contains("credentials") || lower.endsWith(".pem")
                || lower.endsWith(".key") || lower.contains("id_rsa") || lower.endsWith(".jnativeignore")) {
            return false;
        }
        if (name.startsWith(".git/") || name.contains("/.git/")) {
            return false;
        }
        return true;
    }

    public static void writeAtomic(Path dest, byte[] data) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
