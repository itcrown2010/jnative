/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/** Wildcard: {@code ?}, {@code ? extends T}, {@code ? super T}.
 * @author 张晓达
 */
public record WildcardType(Kind kind, JType bound) implements JType {

    public enum Kind {
        UNBOUNDED,
        EXTENDS,
        SUPER
    }

    public static WildcardType unbounded() {
        return new WildcardType(Kind.UNBOUNDED, null);
    }

    public static WildcardType extendsBound(JType bound) {
        return new WildcardType(Kind.EXTENDS, bound);
    }

    public static WildcardType superBound(JType bound) {
        return new WildcardType(Kind.SUPER, bound);
    }

    @Override
    public String displayName() {
        return switch (kind) {
            case UNBOUNDED -> "?";
            case EXTENDS -> "? extends " + bound.displayName();
            case SUPER -> "? super " + bound.displayName();
        };
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType || other instanceof NullType) {
            return true;
        }
        // Lenient: an unbounded '?' parameter (e.g. Comparable<?>.compareTo) takes any reference.
        return kind == Kind.UNBOUNDED && other.isReference();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
