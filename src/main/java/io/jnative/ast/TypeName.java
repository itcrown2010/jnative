package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Source-level type name as written in the program.
 * {@code typeArgument} is used for compiler-recognized {@code NativePtr<T>}.
 */
public record TypeName(Kind kind, String name, TypeName typeArgument, SourceLocation location) {

    public enum Kind {
        VOID,
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        CHAR,
        CLASS,
        NATIVE_PTR
    }

    public TypeName(Kind kind, String name, SourceLocation location) {
        this(kind, name, null, location);
    }

    public static TypeName ofVoid(SourceLocation loc) {
        return new TypeName(Kind.VOID, "void", loc);
    }

    public static TypeName ofByte(SourceLocation loc) {
        return new TypeName(Kind.BYTE, "byte", loc);
    }

    public static TypeName ofShort(SourceLocation loc) {
        return new TypeName(Kind.SHORT, "short", loc);
    }

    public static TypeName ofInt(SourceLocation loc) {
        return new TypeName(Kind.INT, "int", loc);
    }

    public static TypeName ofLong(SourceLocation loc) {
        return new TypeName(Kind.LONG, "long", loc);
    }

    public static TypeName ofFloat(SourceLocation loc) {
        return new TypeName(Kind.FLOAT, "float", loc);
    }

    public static TypeName ofDouble(SourceLocation loc) {
        return new TypeName(Kind.DOUBLE, "double", loc);
    }

    public static TypeName ofBoolean(SourceLocation loc) {
        return new TypeName(Kind.BOOLEAN, "boolean", loc);
    }

    public static TypeName ofChar(SourceLocation loc) {
        return new TypeName(Kind.CHAR, "char", loc);
    }

    public static TypeName ofClass(String name, SourceLocation loc) {
        return new TypeName(Kind.CLASS, name, loc);
    }

    public static TypeName ofNativePtr(TypeName arg, SourceLocation loc) {
        return new TypeName(Kind.NATIVE_PTR, "NativePtr", arg, loc);
    }

    @Override
    public String toString() {
        if (kind == Kind.NATIVE_PTR) {
            return "NativePtr<" + (typeArgument != null ? typeArgument : "Void") + ">";
        }
        return name;
    }
}
