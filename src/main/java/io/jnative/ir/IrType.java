/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ir;

import io.jnative.semantic.ArrayType;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.InterfaceType;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.JType;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.NullType;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.VoidType;

/**
 * @author 张晓达
 */
public enum IrType {
    VOID,
    I1,
    I8,
    I16,
    I32,
    I64,
    FLOAT,
    DOUBLE,
    /** Managed Java reference (GC root eligible). */
    PTR,
    /** Native / FFI pointer (not a GC object root). */
    NATIVE_PTR;

    public static IrType from(JType type) {
        return switch (type) {
            case VoidType ignored -> VOID;
            case PrimitiveType p -> switch (p) {
                case BOOLEAN -> I1;
                case BYTE -> I8;
                case CHAR, SHORT -> I16;
                case INT -> I32;
                case LONG -> I64;
                case FLOAT -> FLOAT;
                case DOUBLE -> DOUBLE;
            };
            case ClassType ignored -> PTR;
            case InterfaceType ignored -> PTR;
            case io.jnative.semantic.ParameterizedType ignored -> PTR;
            case io.jnative.semantic.TypeVariable ignored -> PTR;
            case io.jnative.semantic.WildcardType ignored -> PTR;
            case ArrayType ignored -> PTR;
            case NativePtrType ignored -> NATIVE_PTR;
            case NativeStructType ignored -> NATIVE_PTR; // stack locals held as address
            case NullType ignored -> PTR;
            case ErrorType ignored -> I32;
            case io.jnative.semantic.FunctionType ignored -> PTR;
            case io.jnative.semantic.NativeFunctionPtrType ignored -> NATIVE_PTR;
        };
    }

    public boolean isIntegerLike() {
        return this == I1 || this == I8 || this == I16 || this == I32 || this == I64;
    }

    public boolean isManagedPointer() {
        return this == PTR;
    }

    public String llvm() {
        return switch (this) {
            case VOID -> "void";
            case I1 -> "i1";
            case I8 -> "i8";
            case I16 -> "i16";
            case I32 -> "i32";
            case I64 -> "i64";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case PTR, NATIVE_PTR -> "ptr";
        };
    }

    @Override
    public String toString() {
        return llvm();
    }
}
