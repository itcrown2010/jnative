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

    /**
     * Views {@code actual} (parameterized or plain class/interface type) as an instantiation of the
     * generic {@code target} (e.g. {@code ArrayList<Integer>} as {@code Collection<Integer>}),
     * walking implemented interfaces and super-interfaces with type-argument substitution.
     * Returns null when {@code actual} is not a subtype of {@code target} or carries no type arguments.
     */
    public static ParameterizedType asSuper(JType actual, ReferenceTypeSymbol target) {
        return asSuper(actual, target, 0);
    }

    private static ParameterizedType asSuper(JType actual, ReferenceTypeSymbol target, int depth) {
        if (depth > 16) {
            return null;
        }
        ReferenceTypeSymbol raw;
        List<JType> args;
        if (actual instanceof ParameterizedType p) {
            raw = p.raw();
            args = p.typeArguments();
        } else if (actual instanceof ClassType c) {
            raw = c.classSymbol();
            args = List.of();
        } else if (actual instanceof InterfaceType it) {
            raw = it.interfaceSymbol();
            args = List.of();
        } else {
            return null;
        }
        if (raw == target) {
            return actual instanceof ParameterizedType p ? p : null;
        }
        List<TypeParameterSymbol> params = raw instanceof ClassSymbol cs ? cs.typeParameters()
                : ((InterfaceSymbol) raw).typeParameters();
        io.jnative.generic.TypeSubstitution sub = (!params.isEmpty() && params.size() == args.size())
                ? io.jnative.generic.TypeSubstitution.zip(params, args) : null;
        List<ParameterizedType> supers = new java.util.ArrayList<>();
        if (raw instanceof ClassSymbol cs) {
            supers.addAll(cs.implementedParameterizedInterfaces());
        } else {
            supers.addAll(((InterfaceSymbol) raw).parameterizedSuperInterfaces());
        }
        for (ParameterizedType sup : supers) {
            List<JType> mapped = new java.util.ArrayList<>();
            for (JType a : sup.typeArguments()) {
                mapped.add(sub != null ? sub.apply(a) : a);
            }
            ParameterizedType found = asSuper(new ParameterizedType(sup.raw(), mapped), target, depth + 1);
            if (found != null) {
                return found;
            }
        }
        if (raw instanceof ClassSymbol cs && cs.superClass().isPresent()) {
            return asSuper(cs.superClass().get().asType(), target, depth + 1);
        }
        return null;
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
            // ArrayList<Integer> → Collection<Integer>, Set<Integer> → Collection<Integer>, ...
            ParameterizedType viewed = asSuper(pt, raw);
            if (viewed != null && viewed.typeArguments.size() == typeArguments.size()) {
                for (int i = 0; i < typeArguments.size(); i++) {
                    if (!typeEquals(typeArguments.get(i), viewed.typeArguments.get(i))) {
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
                // Direct "implements Iface<...>" declaration: map the class's own type parameters
                // (e.g. class Values<K, V> implements Collection<V>) instead of comparing positionally.
                for (ClassSymbol c = cs; c != null; c = c.superClass().orElse(null)) {
                    for (ParameterizedType declared : c.implementedParameterizedInterfaces()) {
                        if (declared.raw() == iface
                                && declared.typeArguments().size() == typeArguments.size()
                                && c == cs
                                && cs.typeParameters().size() == pt.typeArguments().size()) {
                            io.jnative.generic.TypeSubstitution sub = io.jnative.generic.TypeSubstitution.zip(
                                    cs.typeParameters(), pt.typeArguments());
                            for (int i = 0; i < typeArguments.size(); i++) {
                                if (!typeEquals(typeArguments.get(i), sub.apply(declared.typeArguments().get(i)))) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    }
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
