package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Source-level type name as written in the program.
 */
public record TypeName(Kind kind, String name, SourceLocation location) {

    public enum Kind {
        VOID,
        INT,
        LONG,
        DOUBLE,
        BOOLEAN,
        CHAR,
        CLASS
    }

    public static TypeName ofVoid(SourceLocation loc) {
        return new TypeName(Kind.VOID, "void", loc);
    }

    public static TypeName ofInt(SourceLocation loc) {
        return new TypeName(Kind.INT, "int", loc);
    }

    public static TypeName ofLong(SourceLocation loc) {
        return new TypeName(Kind.LONG, "long", loc);
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

    @Override
    public String toString() {
        return name;
    }
}
