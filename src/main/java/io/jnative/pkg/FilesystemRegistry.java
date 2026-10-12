/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filesystem / local test registry.
 * Layout: root/namespace/name/version/{package.jnpkg,meta.toml,yanked}
 * @author 张晓达
 */
public final class FilesystemRegistry implements PackageRegistry {
    private final Path root;
    private final String name;

    public FilesystemRegistry(Path root) {
        this(root, "local");
    }

    public FilesystemRegistry(Path root, String name) {
        this.root = root.toAbsolutePath().normalize();
        this.name = name == null ? "local" : name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String url() {
        return "file://" + root;
    }

    public Path root() {
        return root;
    }

    @Override
    public List<PackageId> search(String query) throws IOException {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<PackageId> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> ns = Files.list(root)) {
            for (Path nsp : ns.filter(Files::isDirectory).toList()) {
                try (Stream<Path> pkgs = Files.list(nsp)) {
                    for (Path pkg : pkgs.filter(Files::isDirectory).toList()) {
                        PackageId id = new PackageId(nsp.getFileName().toString(), pkg.getFileName().toString(), url());
                        if (q.isEmpty() || id.coordinate().toLowerCase(Locale.ROOT).contains(q)
                                || id.name().toLowerCase(Locale.ROOT).contains(q)) {
                            out.add(id);
                        }
                    }
                }
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    @Override
    public List<SemanticVersion> versions(PackageId id) throws IOException {
        Path dir = packageDir(id);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<SemanticVersion> out = new ArrayList<>();
        try (Stream<Path> vers = Files.list(dir)) {
            for (Path v : vers.filter(Files::isDirectory).toList()) {
                try {
                    out.add(SemanticVersion.parse(v.getFileName().toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    @Override
    public Optional<DependencyResolver.PackageMeta> metadata(PackageId id, SemanticVersion version) throws IOException {
        Path dir = versionDir(id, version);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        Path meta = dir.resolve("meta.toml");
        PackageManifest m;
        if (Files.isRegularFile(meta)) {
            m = ManifestFile.load(meta);
        } else {
            m = new PackageManifest();
            m.setId(id);
            m.setVersion(version);
        }
        Path pkg = dir.resolve("package.jnpkg");
        String hash = "";
        if (Files.isRegularFile(pkg)) {
            hash = ContentHasher.sha256(Files.readAllBytes(pkg));
        }
        Path hashFile = dir.resolve("SHA256");
        if (Files.isRegularFile(hashFile)) {
            hash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();
        }
        boolean yanked = Files.isRegularFile(dir.resolve("yanked"));
        return Optional.of(new DependencyResolver.PackageMeta(
                id.withSource(url()),
                version,
                m.dependencies(),
                m.requires(),
                m.features(),
                hash,
                url(),
                yanked,
                m.packageType(),
                dir.toString(),
                m.nativeLibraries()
        ));
    }

    @Override
    public byte[] download(PackageId id, SemanticVersion version) throws IOException {
        Path pkg = versionDir(id, version).resolve("package.jnpkg");
        if (!Files.isRegularFile(pkg)) {
            throw new IOException("404 package not found: " + id + "@" + version);
        }
        byte[] data = Files.readAllBytes(pkg);
        Path hashFile = versionDir(id, version).resolve("SHA256");
        if (Files.isRegularFile(hashFile)) {
            String expected = Files.readString(hashFile, StandardCharsets.UTF_8).trim();
            String actual = ContentHasher.sha256(data);
            if (!expected.equals(actual)) {
                throw new IOException("SECURITY ERROR: hash mismatch for " + id + "@" + version);
            }
        }
        return data;
    }

    @Override
    public void publish(PackageId id, SemanticVersion version, byte[] archive, DependencyResolver.PackageMeta meta)
            throws IOException {
        if (archive == null || archive.length == 0) {
            throw new IOException("empty package archive");
        }
        String hash = ContentHasher.sha256(archive);
        Path dir = versionDir(id, version);
        Files.createDirectories(dir);
        PackageArchive.writeAtomic(dir.resolve("package.jnpkg"), archive);
        Files.writeString(dir.resolve("SHA256"), hash + "\n", StandardCharsets.UTF_8);
        PackageManifest m = new PackageManifest();
        m.setId(id);
        m.setVersion(version);
        if (meta != null) {
            m.dependencies().addAll(meta.dependencies());
            m.requires().addAll(meta.requires());
            m.features().addAll(meta.features());
            m.setPackageType(meta.packageType());
            m.nativeLibraries().addAll(meta.nativeLibraries());
        }
        ManifestFile.saveToml(dir.resolve("meta.toml"), m);
    }

    @Override
    public void yank(PackageId id, SemanticVersion version) throws IOException {
        Path dir = versionDir(id, version);
        if (!Files.isDirectory(dir)) {
            throw new IOException("cannot yank missing version: " + id + "@" + version);
        }
        Files.writeString(dir.resolve("yanked"), "yanked\n", StandardCharsets.UTF_8);
    }

    @Override
    public boolean isYanked(PackageId id, SemanticVersion version) throws IOException {
        return Files.isRegularFile(versionDir(id, version).resolve("yanked"));
    }

    private Path packageDir(PackageId id) {
        return root.resolve(id.namespace()).resolve(id.name());
    }

    private Path versionDir(PackageId id, SemanticVersion version) {
        return packageDir(id).resolve(version.toString());
    }

    /** Composite index over this registry for the resolver. */
    public DependencyResolver.Index asIndex() {
        return new DependencyResolver.Index() {
            @Override
            public List<DependencyResolver.PackageMeta> versions(PackageId id) {
                try {
                    List<DependencyResolver.PackageMeta> out = new ArrayList<>();
                    for (SemanticVersion v : FilesystemRegistry.this.versions(id)) {
                        metadata(id, v).ifPresent(out::add);
                    }
                    return out;
                } catch (IOException e) {
                    return List.of();
                }
            }

            @Override
            public DependencyResolver.PackageMeta get(PackageId id, SemanticVersion version) {
                try {
                    return metadata(id, version).orElse(null);
                } catch (IOException e) {
                    return null;
                }
            }
        };
    }
}
