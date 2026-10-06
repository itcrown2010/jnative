package io.jnative.ffi;

/**
 * Provenance of a NativePtr value for escape / lifetime analysis.
 */
public enum PointerProvenance {
    STACK,
    NATIVE_HEAP,
    EXTERNAL,
    NULL,
    UNKNOWN
}
