/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * @author 张晓达
 */
public record ArrayType(JType elementType) implements JType {

    @Override
    public String displayName() {
        return elementType.displayName() + "[]";
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
        if (other instanceof ArrayType at) {
            // Java-style array covariance for Object[] only: any reference-element array
            // (String[], Foo[], ...) is an Object[]. Primitive element arrays never match.
            if (elementType instanceof ClassType c && c.name().equals("Object")
                    && !at.elementType().isPrimitive()) {
                return true;
            }
            // T[] (type parameter element): any reference-element array; the generic call is
            // bound/inferred afterwards (the method body is erased, arrays of references share a layout).
            if (elementType instanceof TypeVariable && !at.elementType().isPrimitive()) {
                return true;
            }
            return elementType.isAssignableFrom(at.elementType())
                    && at.elementType().isAssignableFrom(elementType);
        }
        return false;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
