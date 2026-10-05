package io.jnative.ir;

import io.jnative.semantic.ArrayType;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.JType;
import io.jnative.semantic.NullType;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.VoidType;

public enum IrType {
    VOID,
    I1,
    I16,
    I32,
    I64,
    DOUBLE,
    PTR;

    public static IrType from(JType type) {
        return switch (type) {
            case VoidType ignored -> VOID;
            case PrimitiveType p -> switch (p) {
                case BOOLEAN -> I1;
                case CHAR -> I16;
                case INT -> I32;
                case LONG -> I64;
                case DOUBLE -> DOUBLE;
            };
            case ClassType ignored -> PTR;
            case ArrayType ignored -> PTR;
            case NullType ignored -> PTR;
            case ErrorType ignored -> I32;
        };
    }

    public boolean isIntegerLike() {
        return this == I1 || this == I16 || this == I32 || this == I64;
    }

    public String llvm() {
        return switch (this) {
            case VOID -> "void";
            case I1 -> "i1";
            case I16 -> "i16";
            case I32 -> "i32";
            case I64 -> "i64";
            case DOUBLE -> "double";
            case PTR -> "ptr";
        };
    }

    @Override
    public String toString() {
        return llvm();
    }
}
