/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

/** System V AMD64 (Linux/macOS x86_64).
 * @author 张晓达
 */
public final class X86_64SystemVAbi implements TargetAbi {
    public static final X86_64SystemVAbi INSTANCE = new X86_64SystemVAbi();

    private X86_64SystemVAbi() {
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
        return switch (javaTypeName) {
            case "void" -> AbiType.VOID;
            case "boolean" -> AbiType.I32;
            case "byte" -> AbiType.I8;
            case "short" -> AbiType.I16;
            case "char" -> AbiType.U16;
            case "int" -> AbiType.I32;
            case "long" -> AbiType.I64;
            case "float" -> AbiType.F32;
            case "double" -> AbiType.F64;
            case "NativePtr" -> AbiType.PTR;
            case "String" -> AbiType.CSTRING;
            default -> throw new IllegalArgumentException("unsupported native type: " + javaTypeName);
        };
    }
}
