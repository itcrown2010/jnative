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

/**
 * Minimal constraint-based inference for generic method / diamond calls.
 * Constraints: {@code T = X}, {@code T <: X}, {@code X <: T}.
 * @author 张晓达
 */
public final class TypeInferenceEngine {

    public enum Kind {
        EQUAL,
        SUBTYPE,   // left <: right
        SUPERTYPE  // left >: right  (i.e. right <: left)
    }

    public record Constraint(Kind kind, JType left, JType right) {
    }

    public static final class ConstraintSet {
        private final List<Constraint> constraints = new ArrayList<>();

        public void equal(JType a, JType b) {
            constraints.add(new Constraint(Kind.EQUAL, a, b));
        }

        public void subtype(JType a, JType b) {
            constraints.add(new Constraint(Kind.SUBTYPE, a, b));
        }

        public List<Constraint> constraints() {
            return List.copyOf(constraints);
        }
    }

    /** {@code defaulted}: some type parameter was unconstrained and fell back to the default (Object). */
    public record InferenceResult(boolean success, TypeSubstitution substitution, String message, boolean defaulted) {
        public static InferenceResult ok(TypeSubstitution sub) {
            return new InferenceResult(true, sub, null, false);
        }

        public static InferenceResult ok(TypeSubstitution sub, boolean defaulted) {
            return new InferenceResult(true, sub, null, defaulted);
        }

        public static InferenceResult fail(String message) {
            return new InferenceResult(false, new TypeSubstitution(Map.of()), message, false);
        }
    }

    private TypeInferenceEngine() {
    }

    /**
     * Infer method type parameters from argument types and optional target return type.
     */
    public static InferenceResult inferMethod(
            List<TypeParameterSymbol> typeParams,
            List<JType> formalParams,
            List<JType> actualArgs,
            JType declaredReturn,
            JType targetReturn
    ) {
        return inferMethod(typeParams, formalParams, actualArgs, declaredReturn, targetReturn, null);
    }

    /** As above; type parameters left unconstrained default to {@code unconstrainedDefault} (Java: Object). */
    public static InferenceResult inferMethod(
            List<TypeParameterSymbol> typeParams,
            List<JType> formalParams,
            List<JType> actualArgs,
            JType declaredReturn,
            JType targetReturn,
            JType unconstrainedDefault
    ) {
        if (typeParams == null || typeParams.isEmpty()) {
            return InferenceResult.ok(new TypeSubstitution(Map.of()));
        }
        ConstraintSet set = new ConstraintSet();
        int n = Math.min(formalParams.size(), actualArgs.size());
        for (int i = 0; i < n; i++) {
            if (actualArgs.get(i) == null) {
                continue; // weakly typed argument (e.g. Collections.reverseOrder()): no constraint
            }
            addEqualityConstraints(set, formalParams.get(i), actualArgs.get(i));
        }
        if (targetReturn != null && declaredReturn != null) {
            addEqualityConstraints(set, declaredReturn, targetReturn);
        }
        return solve(typeParams, set, unconstrainedDefault);
    }

    private static void addEqualityConstraints(ConstraintSet set, JType formal, JType actual) {
        if (formal instanceof TypeVariable) {
            set.equal(formal, actual);
            return;
        }
        if (formal instanceof ParameterizedType fp2 && !(actual instanceof ParameterizedType ap0 && ap0.raw().name().equals(fp2.raw().name()))) {
            ParameterizedType viewed = ParameterizedType.asSuper(actual, fp2.raw());
            if (viewed != null) {
                actual = viewed;
            }
        }
        if (formal instanceof ParameterizedType fp && actual instanceof ParameterizedType ap) {
            if (fp.raw().name().equals(ap.raw().name())
                    && fp.typeArguments().size() == ap.typeArguments().size()) {
                for (int i = 0; i < fp.typeArguments().size(); i++) {
                    addEqualityConstraints(set, fp.typeArguments().get(i), ap.typeArguments().get(i));
                }
            }
            return;
        }
        if (formal instanceof ArrayType fa && actual instanceof ArrayType aa) {
            addEqualityConstraints(set, fa.elementType(), aa.elementType());
        }
    }

    private static InferenceResult solve(List<TypeParameterSymbol> typeParams, ConstraintSet set,
            JType unconstrainedDefault) {
        Map<TypeParameterSymbol, JType> bound = new LinkedHashMap<>();
        for (Constraint c : set.constraints()) {
            if (c.kind() != Kind.EQUAL) {
                continue;
            }
            if (c.left() instanceof TypeVariable tv) {
                TypeParameterSymbol sym = tv.symbol();
                if (!typeParams.contains(sym)) {
                    // match by name
                    for (TypeParameterSymbol p : typeParams) {
                        if (p.name().equals(sym.name())) {
                            sym = p;
                            break;
                        }
                    }
                }
                JType existing = bound.get(sym);
                if (existing == null) {
                    bound.put(sym, stripWildcard(c.right()));
                } else if (!sameType(existing, c.right())) {
                    return InferenceResult.fail(
                            "cannot infer type parameter " + sym.name()
                                    + ": conflicting constraints " + existing + " vs " + c.right());
                }
            } else if (c.right() instanceof TypeVariable tv) {
                TypeParameterSymbol sym = tv.symbol();
                for (TypeParameterSymbol p : typeParams) {
                    if (p.name().equals(sym.name())) {
                        sym = p;
                        break;
                    }
                }
                JType existing = bound.get(sym);
                if (existing == null) {
                    bound.put(sym, stripWildcard(c.left()));
                } else if (!sameType(existing, c.left())) {
                    return InferenceResult.fail(
                            "cannot infer type parameter " + sym.name()
                                    + ": conflicting constraints " + existing + " vs " + c.left());
                }
            }
        }
        boolean defaulted = false;
        for (TypeParameterSymbol p : typeParams) {
            if (!bound.containsKey(p)) {
                if (unconstrainedDefault == null) {
                    return InferenceResult.fail("cannot infer type parameter " + p.name());
                }
                bound.put(p, unconstrainedDefault);
                defaulted = true;
            }
            for (JType declared : p.bounds()) {
                JType arg = bound.get(p);
                // F-bounded (T extends Comparable<T>): compare against the bound with T := arg.
                JType b = new TypeSubstitution(bound).apply(declared);
                if (!b.isAssignableFrom(arg)) {
                    return InferenceResult.fail(
                            "type argument " + arg + " does not satisfy bound " + b);
                }
            }
        }
        return InferenceResult.ok(new TypeSubstitution(bound), defaulted);
    }

    private static JType stripWildcard(JType t) {
        if (t instanceof WildcardType w) {
            return w.bound() != null ? w.bound() : t;
        }
        return t;
    }

    private static boolean sameType(JType a, JType b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.displayName().equals(b.displayName());
    }
}
