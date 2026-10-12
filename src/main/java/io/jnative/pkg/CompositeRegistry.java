/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Multiple registries with deterministic source precedence (first wins).
 * Prevents dependency confusion: private before public.
 * @author 张晓达
 */
public final class CompositeRegistry implements PackageRegistry {
    private final List<PackageRegistry> registries = new ArrayList<>();

    public CompositeRegistry add(PackageRegistry registry) {
        registries.add(registry);
        return this;
    }

    public List<PackageRegistry> registries() {
        return registries;
    }

    public DependencyResolver.Index asIndex() {
        return new DependencyResolver.Index() {
            @Override
            public List<DependencyResolver.PackageMeta> versions(PackageId id) {
                try {
                    List<DependencyResolver.PackageMeta> out = new ArrayList<>();
                    for (PackageRegistry r : registries) {
                        List<SemanticVersion> vers = r.versions(id);
                        if (vers.isEmpty()) {
                            continue;
                        }
                        for (SemanticVersion v : vers) {
                            r.metadata(id, v).ifPresent(out::add);
                        }
                        return out; // first registry owning the package
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

    @Override
    public String name() {
        return "composite";
    }

    @Override
    public String url() {
        return registries.isEmpty() ? "" : registries.get(0).url();
    }

    @Override
    public List<PackageId> search(String query) throws IOException {
        Map<PackageId, PackageId> seen = new LinkedHashMap<>();
        for (PackageRegistry r : registries) {
            for (PackageId id : r.search(query)) {
                seen.putIfAbsent(id, id.withSource(r.url()));
            }
        }
        return new ArrayList<>(seen.values());
    }

    @Override
    public List<SemanticVersion> versions(PackageId id) throws IOException {
        for (PackageRegistry r : registries) {
            List<SemanticVersion> v = r.versions(id);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return List.of();
    }

    @Override
    public Optional<DependencyResolver.PackageMeta> metadata(PackageId id, SemanticVersion version) throws IOException {
        for (PackageRegistry r : registries) {
            Optional<DependencyResolver.PackageMeta> m = r.metadata(id, version);
            if (m.isPresent()) {
                return m;
            }
        }
        return Optional.empty();
    }

    @Override
    public byte[] download(PackageId id, SemanticVersion version) throws IOException {
        IOException last = null;
        for (PackageRegistry r : registries) {
            try {
                if (r.metadata(id, version).isPresent()) {
                    return r.download(id, version);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("package not found in any registry: " + id + "@" + version);
    }

    @Override
    public void publish(PackageId id, SemanticVersion version, byte[] archive, DependencyResolver.PackageMeta meta)
            throws IOException {
        if (registries.isEmpty()) {
            throw new IOException("no registry configured");
        }
        registries.get(0).publish(id, version, archive, meta);
    }

    @Override
    public void yank(PackageId id, SemanticVersion version) throws IOException {
        registries.get(0).yank(id, version);
    }

    @Override
    public boolean isYanked(PackageId id, SemanticVersion version) throws IOException {
        return metadata(id, version).map(DependencyResolver.PackageMeta::yanked).orElse(false);
    }
}
