package io.jnative.backend;

/**
 * Compile-target abstraction separating Native LLVM linking from WASM browser output.
 */
public interface TargetBackend {
    String triple();

    CompileArtifact compile(BackendRequest request) throws Exception;
}
