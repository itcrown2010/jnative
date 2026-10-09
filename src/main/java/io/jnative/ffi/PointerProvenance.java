package io.jnative.ffi;

/**
 * Provenance of a NativePtr value for escape / lifetime analysis.
 * @author 张晓达
 */
public enum PointerProvenance {
    STACK,
    NATIVE_HEAP,
    EXTERNAL,
    NULL,
    UNKNOWN
}
