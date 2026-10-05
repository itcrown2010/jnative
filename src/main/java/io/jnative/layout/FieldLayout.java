package io.jnative.layout;

/**
 * Per-field layout within a class instance.
 */
public record FieldLayout(
        String name,
        int index,
        long offset,
        int size,
        int alignment,
        boolean isReference,
        String llvmType
) {
}
