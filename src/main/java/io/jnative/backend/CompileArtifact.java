package io.jnative.backend;

import java.nio.file.Path;
import java.util.List;

/** Artifacts produced by a {@link TargetBackend}. */
public record CompileArtifact(
        Path primaryOutput,
        List<Path> secondaryOutputs,
        String message
) {
    public static CompileArtifact of(Path primary, Path... more) {
        return new CompileArtifact(primary, List.of(more), "");
    }
}
