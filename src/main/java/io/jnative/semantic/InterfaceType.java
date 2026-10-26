/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * Interface reference type (distinct from {@link ClassType}).
 * @author 张晓达
 */
public record InterfaceType(InterfaceSymbol interfaceSymbol) implements JType {

    public String name() {
        return interfaceSymbol.name();
    }

    @Override
    public String displayName() {
        return interfaceSymbol.name();
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
        if (other instanceof InterfaceType it) {
            return it.interfaceSymbol().isSubtypeOf(interfaceSymbol);
        }
        if (other instanceof ClassType ct) {
            if (interfaceSymbol.functionType() != null && ct.classSymbol().functionType() != null) {
                return interfaceSymbol.functionType().isAssignableFrom(ct.classSymbol().functionType())
                        || ct.classSymbol().implementsInterface(interfaceSymbol);
            }
            return ct.classSymbol().implementsInterface(interfaceSymbol);
        }
        // Box<Integer> → Task (generic class implementing a non-generic interface).
        if (other instanceof ParameterizedType pt) {
            if (pt.raw() instanceof ClassSymbol cs) {
                return cs.implementsInterface(interfaceSymbol);
            }
            if (pt.raw() instanceof InterfaceSymbol is) {
                return is.isSubtypeOf(interfaceSymbol);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
