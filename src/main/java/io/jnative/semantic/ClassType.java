/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * User-defined or builtin class type (e.g. Person, String).
 * @author 张晓达
 */
public record ClassType(ClassSymbol classSymbol) implements JType {

    public String name() {
        return classSymbol.name();
    }

    @Override
    public String displayName() {
        return classSymbol.name();
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
        if (other instanceof ErrorType) {
            return true;
        }
        if (other instanceof NullType) {
            return true;
        }
        if (other instanceof ClassType ct) {
            if (classSymbol.functionType() != null && ct.classSymbol().functionType() != null) {
                return classSymbol.functionType().isAssignableFrom(ct.classSymbol().functionType())
                        || ct.classSymbol().isSubtypeOf(classSymbol);
            }
            return ct.classSymbol().isSubtypeOf(this.classSymbol);
        }
        // ListView<String> → Component / Control (0.18 UI generics).
        if (other instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
            return cs.isSubtypeOf(this.classSymbol);
        }
        // Type variables erase to Object (or a bound); treat as assignable to reference types.
        if (other instanceof TypeVariable) {
            return true;
        }
        // Every interface-typed reference is an object: assignable to Object.
        if ("Object".equals(classSymbol.name())) {
            if (other instanceof InterfaceType) {
                return true;
            }
            if (other instanceof ParameterizedType pt && pt.raw() instanceof InterfaceSymbol) {
                return true;
            }
        }
        // Interface → other classes only via null/Error (already handled); not generally assignable.
        return false;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
