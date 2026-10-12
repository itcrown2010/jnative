/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.generic;

import io.jnative.semantic.ArrayType;
import io.jnative.semantic.JType;
import io.jnative.semantic.ParameterizedType;
import io.jnative.semantic.TypeParameterSymbol;
import io.jnative.semantic.TypeVariable;
import io.jnative.semantic.WildcardType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recursive type-variable substitution.
 * @author 张晓达
 */
public final class TypeSubstitution {
    private final Map<TypeParameterSymbol, JType> map;

    public TypeSubstitution(Map<TypeParameterSymbol, JType> map) {
        this.map = Map.copyOf(map);
    }

    public static TypeSubstitution of(TypeParameterSymbol param, JType arg) {
        Map<TypeParameterSymbol, JType> m = new LinkedHashMap<>();
        m.put(param, arg);
        return new TypeSubstitution(m);
    }

    public static TypeSubstitution zip(List<TypeParameterSymbol> params, List<JType> args) {
        Map<TypeParameterSymbol, JType> m = new LinkedHashMap<>();
        int n = Math.min(params.size(), args.size());
        for (int i = 0; i < n; i++) {
            m.put(params.get(i), args.get(i));
        }
        return new TypeSubstitution(m);
    }

    public JType apply(JType type) {
        return apply(type, new java.util.HashSet<>());
    }

    private JType apply(JType type, java.util.Set<TypeParameterSymbol> visiting) {
        if (type == null) {
            return null;
        }
        if (type instanceof TypeVariable tv) {
            if (!visiting.add(tv.symbol())) {
                return type; // cycle guard (e.g. mistaken T→T maps)
            }
            JType mapped = map.get(tv.symbol());
            if (mapped != null) {
                return apply(mapped, visiting);
            }
            // Match by name+owner if identity differs
            for (var e : map.entrySet()) {
                if (e.getKey().name().equals(tv.symbol().name())
                        && sameOwner(e.getKey(), tv.symbol())) {
                    return apply(e.getValue(), visiting);
                }
            }
            return type;
        }
        if (type instanceof ParameterizedType pt) {
            List<JType> args = new ArrayList<>();
            for (JType a : pt.typeArguments()) {
                args.add(apply(a));
            }
            return new ParameterizedType(pt.raw(), args);
        }
        if (type instanceof ArrayType at) {
            return new ArrayType(apply(at.elementType()));
        }
        if (type instanceof WildcardType w) {
            return switch (w.kind()) {
                case UNBOUNDED -> w;
                case EXTENDS -> WildcardType.extendsBound(apply(w.bound()));
                case SUPER -> WildcardType.superBound(apply(w.bound()));
            };
        }
        return type;
    }

    private static boolean sameOwner(TypeParameterSymbol a, TypeParameterSymbol b) {
        if (a.ownerType() != null && b.ownerType() != null) {
            return a.ownerType().name().equals(b.ownerType().name());
        }
        if (a.ownerMethod() != null && b.ownerMethod() != null) {
            return a.ownerMethod().mangledName().equals(b.ownerMethod().mangledName());
        }
        return a.ownerType() == null && b.ownerType() == null
                && a.ownerMethod() == null && b.ownerMethod() == null;
    }

    public Map<TypeParameterSymbol, JType> asMap() {
        return map;
    }
}
