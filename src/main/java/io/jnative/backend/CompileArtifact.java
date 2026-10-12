/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

import java.nio.file.Path;
import java.util.List;

/** Artifacts produced by a {@link TargetBackend}.
 * @author 张晓达
 */
public record CompileArtifact(
        Path primaryOutput,
        List<Path> secondaryOutputs,
        String message
) {
    public static CompileArtifact of(Path primary, Path... more) {
        return new CompileArtifact(primary, List.of(more), "");
    }
}
