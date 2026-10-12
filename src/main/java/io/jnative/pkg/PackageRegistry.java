/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Registry abstraction — local filesystem or HTTP.
 * @author 张晓达
 */
public interface PackageRegistry {
    String name();

    String url();

    List<PackageId> search(String query) throws IOException;

    List<SemanticVersion> versions(PackageId id) throws IOException;

    Optional<DependencyResolver.PackageMeta> metadata(PackageId id, SemanticVersion version) throws IOException;

    byte[] download(PackageId id, SemanticVersion version) throws IOException;

    void publish(PackageId id, SemanticVersion version, byte[] archive, DependencyResolver.PackageMeta meta)
            throws IOException;

    void yank(PackageId id, SemanticVersion version) throws IOException;

    boolean isYanked(PackageId id, SemanticVersion version) throws IOException;
}
