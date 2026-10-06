package io.jnative.ffi;

/**
 * One field in a {@link NativeStructLayout} result (ABI layout SoT).
 */
public record NativeStructField(
        String name,
        long offset,
        int size,
        int alignment,
        AbiType abiType,
        String llvmType,
        boolean isPadding
) {
    public static NativeStructField padding(long offset, int size) {
        return new NativeStructField(
                "<padding>",
                offset,
                size,
                1,
                AbiType.I8,
                "i8",
                true);
    }
}
