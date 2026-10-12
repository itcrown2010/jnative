/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Source-level type name. Supports {@code Box<String>}, wildcards, and {@code NativePtr<T>}.
 * @author 张晓达
 */
public record TypeName(
        Kind kind,
        String name,
        TypeName typeArgument,
        List<TypeName> typeArguments,
        WildcardKind wildcardKind,
        TypeName wildcardBound,
        SourceLocation location
) {

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
        NATIVE_PTR,
        WILDCARD,
        TYPE_VAR
    }

    public enum WildcardKind {
        NONE,
        UNBOUNDED,
        EXTENDS,
        SUPER
    }

    public TypeName(Kind kind, String name, SourceLocation location) {
        this(kind, name, null, List.of(), WildcardKind.NONE, null, location);
    }

    public TypeName(Kind kind, String name, TypeName typeArgument, SourceLocation location) {
        this(kind, name, typeArgument,
                typeArgument != null ? List.of(typeArgument) : List.of(),
                WildcardKind.NONE, null, location);
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

    public static TypeName ofClass(String name, List<TypeName> args, SourceLocation loc) {
        List<TypeName> copy = args == null ? List.of() : List.copyOf(args);
        TypeName first = copy.isEmpty() ? null : copy.getFirst();
        return new TypeName(Kind.CLASS, name, first, copy, WildcardKind.NONE, null, loc);
    }

    public static TypeName ofNativePtr(TypeName arg, SourceLocation loc) {
        return new TypeName(Kind.NATIVE_PTR, "NativePtr", arg,
                arg != null ? List.of(arg) : List.of(), WildcardKind.NONE, null, loc);
    }

    public static TypeName ofWildcard(WildcardKind kind, TypeName bound, SourceLocation loc) {
        return new TypeName(Kind.WILDCARD, "?", null, List.of(), kind, bound, loc);
    }

    public boolean hasTypeArguments() {
        return typeArguments != null && !typeArguments.isEmpty();
    }

    public boolean isVoid() {
        return kind == Kind.VOID;
    }

    @Override
    public String toString() {
        if (kind == Kind.WILDCARD) {
            return switch (wildcardKind) {
                case UNBOUNDED, NONE -> "?";
                case EXTENDS -> "? extends " + wildcardBound;
                case SUPER -> "? super " + wildcardBound;
            };
        }
        if (kind == Kind.NATIVE_PTR) {
            return "NativePtr<" + (typeArgument != null ? typeArgument : "Void") + ">";
        }
        if (hasTypeArguments()) {
            return name + "<" + typeArguments.stream()
                    .map(TypeName::toString)
                    .collect(Collectors.joining(", ")) + ">";
        }
        return name;
    }
}
