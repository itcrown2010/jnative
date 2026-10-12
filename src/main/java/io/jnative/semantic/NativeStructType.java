/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * Value type representing a {@code @NativeStruct}. Not a managed reference.
 * @author 张晓达
 */
public final class NativeStructType implements JType {
    private final NativeStructSymbol symbol;

    public NativeStructType(NativeStructSymbol symbol) {
        this.symbol = symbol;
    }

    public NativeStructSymbol symbol() {
        return symbol;
    }

    public String name() {
        return symbol.name();
    }

    @Override
    public String displayName() {
        return symbol.name();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return false;
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
        if (other instanceof ErrorType) {
            return true;
        }
        return other instanceof NativeStructType ns && ns.symbol == symbol;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NativeStructType ns && ns.symbol == symbol;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(symbol);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
