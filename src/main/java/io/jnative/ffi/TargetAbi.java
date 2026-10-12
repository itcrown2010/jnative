/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

/**
 * Target ABI facade. Concrete layouts may differ by OS/arch later.
 * Native struct field sizes/alignments must go through this API — never hardcode 8.
 * @author 张晓达
 */
public interface TargetAbi {
    int pointerSize();

    int pointerAlign();

    AbiType sizeType();

    /** Map a Java/JNative surface type name used in native signatures. */
    AbiType mapPrimitiveJavaType(String javaTypeName);

    default int sizeOf(AbiType type) {
        return switch (type) {
            case VOID -> 0;
            case I8, U8 -> 1;
            case I16, U16 -> 2;
            case I32, U32, F32 -> 4;
            case I64, U64, F64, SIZE -> 8;
            case PTR, CSTRING -> pointerSize();
        };
    }

    default int alignOf(AbiType type) {
        return switch (type) {
            case VOID -> 1;
            case I8, U8 -> 1;
            case I16, U16 -> 2;
            case I32, U32, F32 -> 4;
            case I64, U64, F64, SIZE -> 8;
            case PTR, CSTRING -> pointerAlign();
        };
    }

    static TargetAbi forHost() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean windows = os.contains("win");
        boolean aarch64 = arch.contains("aarch64") || arch.contains("arm64");
        if (windows && !aarch64) {
            return X86_64WindowsAbi.INSTANCE;
        }
        if (aarch64) {
            return AArch64Abi.INSTANCE;
        }
        return X86_64SystemVAbi.INSTANCE;
    }
}
