package io.jnative.semantic;

/**
 * Compiler-recognized {@code Native.*} intrinsics (not {@link MethodSymbol}s).
 */
public enum NativeIntrinsic {
    ADDRESS_OF,
    LOAD_INT,
    STORE_INT,
    ALLOCATE_STRUCT
}
