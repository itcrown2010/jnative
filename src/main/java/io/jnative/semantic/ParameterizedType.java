/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Instantiation of a generic class/interface: {@code Box<String>}, {@code Box<int>}.
 * @author 张晓达
 */
public record ParameterizedType(ReferenceTypeSymbol raw, List<JType> typeArguments) implements JType {

    public ParameterizedType {
        typeArguments = List.copyOf(typeArguments);
    }

    public JType rawType() {
        return raw.asJType();
    }

    public String name() {
        return raw.name();
    }

    @Override
    public String displayName() {
        return raw.name() + "<" + typeArguments.stream()
                .map(JType::displayName)
                .collect(Collectors.joining(", ")) + ">";
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
        if (other instanceof ParameterizedType pt) {
            if (raw.name().equals(pt.raw.name())) {
                if (typeArguments.size() != pt.typeArguments.size()) {
                    return false;
                }
                // Invariant: arguments must match exactly.
                for (int i = 0; i < typeArguments.size(); i++) {
                    if (!typeEquals(typeArguments.get(i), pt.typeArguments.get(i))) {
                        return false;
                    }
                }
                return true;
            }
            // e.g. ArrayListIterator<T> → Iterator<T>
            if (pt.raw() instanceof ClassSymbol cs && raw instanceof InterfaceSymbol iface) {
                if (!cs.implementsInterface(iface)) {
                    return false;
                }
                int n = Math.min(typeArguments.size(), pt.typeArguments.size());
                for (int i = 0; i < n; i++) {
                    if (!typeEquals(typeArguments.get(i), pt.typeArguments.get(i))) {
                        return false;
                    }
                }
                return typeArguments.size() <= pt.typeArguments.size();
            }
            return false;
        }
        // Class implementing a parameterized interface (erasure: type args checked at implements site).
        if (other instanceof ClassType ct && raw instanceof InterfaceSymbol iface) {
            return ct.classSymbol().implementsInterface(iface);
        }
        if (other instanceof ClassType ct && raw instanceof ClassSymbol cs) {
            return ct.classSymbol().isSubtypeOf(cs);
        }
        if (other instanceof InterfaceType it && raw instanceof InterfaceSymbol iface) {
            return it.interfaceSymbol().isSubtypeOf(iface);
        }
        return false;
    }

    private static boolean typeEquals(JType a, JType b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a instanceof ClassType ca && b instanceof ClassType cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof InterfaceType ia && b instanceof InterfaceType ib) {
            return ia.name().equals(ib.name());
        }
        if (a instanceof ParameterizedType pa && b instanceof ParameterizedType pb) {
            return pa.isAssignableFrom(pb) && pb.isAssignableFrom(pa);
        }
        if (a instanceof PrimitiveType && b instanceof PrimitiveType) {
            return a == b;
        }
        if (a instanceof TypeVariable ta && b instanceof TypeVariable tb) {
            return ta.symbol().name().equals(tb.symbol().name());
        }
        return false;
    }

    /** True if any type argument is a primitive (needs specialization). */
    public boolean needsPrimitiveSpecialization() {
        for (JType arg : typeArguments) {
            if (arg instanceof PrimitiveType) {
                return true;
            }
            if (arg instanceof ParameterizedType pt && pt.needsPrimitiveSpecialization()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
