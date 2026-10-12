/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

/** Windows x86_64 ABI stub (same fixed-width mappings for 0.7 primitives).
 * @author 张晓达
 */
public final class X86_64WindowsAbi implements TargetAbi {
    public static final X86_64WindowsAbi INSTANCE = new X86_64WindowsAbi();

    private X86_64WindowsAbi() {
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
