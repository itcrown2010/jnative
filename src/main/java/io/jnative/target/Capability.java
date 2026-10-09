package io.jnative.target;

/**
 * High-level capabilities used in the target matrix (language / runtime / I/O / UI).
 * Distinct from package feature flags in {@link io.jnative.backend.TargetCapability};
 * both layers are projected through {@link TargetProfiles}.
 *
 * @author 张晓达
 */
public enum Capability {
    NUMERICS,
    OBJECTS,
    ARRAYS,
    DYNAMIC_DISPATCH,
    CLOSURES,
    EXCEPTIONS,
    RESOURCE_MANAGEMENT,
    GC,
    THREADS,
    ASYNC_AWAIT,
    FILESYSTEM,
    NETWORK,
    DATABASE,
    UI_DESKTOP,
    UI_BROWSER,
    FFI_NATIVE,
    JS_INTEROP,
    PROCESS,
    TLS_NATIVE,
    HTTP2_NATIVE,
    BROWSER_FETCH,
    JAVA_BRIDGE
}
