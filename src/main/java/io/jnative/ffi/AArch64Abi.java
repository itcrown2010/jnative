/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

/** AArch64 (Apple Silicon / Linux aarch64).
 * @author 张晓达
 */
public final class AArch64Abi implements TargetAbi {
    public static final AArch64Abi INSTANCE = new AArch64Abi();

    private AArch64Abi() {
    }

    @Override
    public int pointerSize() {
        return 8;
    }

    @Override
    public int pointerAlign() {
        return 8;
    }

    @Override
    public AbiType sizeType() {
        return AbiType.SIZE;
    }

    @Override
    public AbiType mapPrimitiveJavaType(String javaTypeName) {
        return X86_64SystemVAbi.INSTANCE.mapPrimitiveJavaType(javaTypeName);
    }
}
