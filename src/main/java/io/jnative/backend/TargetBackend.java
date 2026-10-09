package io.jnative.backend;

/**
 * Compile-target abstraction separating Native LLVM linking from WASM browser output.
 * @author 张晓达
 */
public interface TargetBackend {
    String triple();

    CompileArtifact compile(BackendRequest request) throws Exception;
}
