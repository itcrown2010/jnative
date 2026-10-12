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
