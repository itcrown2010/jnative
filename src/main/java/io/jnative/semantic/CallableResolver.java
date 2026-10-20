/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves overloaded callables: exact → primitive widening → boxing/unboxing.
 * Does not let boxing steal calls that already match via widening (int→long beats int→Long).
 * @author 张晓达
 */
public final class CallableResolver {

    public record Candidate(List<JType> parameterTypes, Object symbol, boolean varargs) {
        public Candidate(List<JType> parameterTypes, Object symbol) {
            this(parameterTypes, symbol, false);
        }
    }

    private enum Phase {
        EXACT,
        WIDENING,
        BOXING
    }

    private CallableResolver() {
    }

    public static Object resolve(List<Candidate> candidates, List<JType> argTypes) {
        Object exact = resolvePhase(candidates, argTypes, Phase.EXACT);
        if (exact != null) {
            return exact;
        }
        Object widen = resolvePhase(candidates, argTypes, Phase.WIDENING);
        if (widen != null) {
            return widen;
        }
        Object boxing = resolvePhase(candidates, argTypes, Phase.BOXING);
        if (boxing != null) {
            return boxing;
        }
        return resolveVarargs(candidates, argTypes);
    }

    /** Variable-arity phase: {@code f(int... xs)} accepts any number of {@code int}s. */
    private static Object resolveVarargs(List<Candidate> candidates, List<JType> argTypes) {
        List<Candidate> matches = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.varargs() && variableArityApplicable(c.parameterTypes(), argTypes)) {
                matches.add(c);
            }
        }
        return matches.size() == 1 ? matches.getFirst().symbol() : null;
    }

    /** True when {@code args} fit the fixed parameters plus zero or more trailing element arguments. */
    public static boolean variableArityApplicable(List<JType> params, List<JType> args) {
        int fixed = params.size() - 1;
        if (fixed < 0 || args.size() < fixed || !(params.getLast() instanceof ArrayType at)) {
            return false;
        }
        for (int i = 0; i < args.size(); i++) {
            JType expected = i < fixed ? params.get(i) : at.elementType();
            if (!Boxing.assignableOrBoxing(expected, args.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when a call to a varargs method must wrap its trailing arguments in a fresh array
     * (as opposed to passing an array directly).
     */
    public static boolean needsVarargsPacking(List<JType> params, List<JType> args) {
        if (args.size() != params.size()) {
            return true;
        }
        JType last = args.getLast();
        JType formal = params.getLast();
        if (last instanceof NullType || Boxing.assignableOrBoxing(formal, last)) {
            return false;
        }
        return true;
    }

    private static Object resolvePhase(List<Candidate> candidates, List<JType> argTypes, Phase phase) {
        List<Candidate> matches = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.parameterTypes().size() != argTypes.size()) {
                continue;
            }
            if (matchesPhase(c.parameterTypes(), argTypes, phase)) {
                matches.add(c);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst().symbol();
        }
        return null;
    }

    private static boolean matchesPhase(List<JType> params, List<JType> args, Phase phase) {
        for (int i = 0; i < args.size(); i++) {
            JType expected = params.get(i);
            JType actual = args.get(i);
            if (!argMatches(expected, actual, phase)) {
                return false;
            }
        }
        return true;
    }

    private static boolean argMatches(JType expected, JType actual, Phase phase) {
        return switch (phase) {
            case EXACT -> sameType(expected, actual);
            case WIDENING -> expected.isAssignableFrom(actual) && !needsBoxing(expected, actual);
            case BOXING -> Boxing.assignableOrBoxing(expected, actual);
        };
    }

    /** True when compatibility requires a boxing conversion (not plain subtype/widen). */
    private static boolean needsBoxing(JType expected, JType actual) {
        if (expected.isAssignableFrom(actual)) {
            return false;
        }
        Boxing.Kind k = Boxing.boxingKind(expected, actual);
        return k != null && k != Boxing.Kind.NONE;
    }

    private static boolean sameType(JType a, JType b) {
        if (a == b) {
            return true;
        }
        if (a instanceof ClassType ca && b instanceof ClassType cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
            return sameType(aa.elementType(), ab.elementType());
        }
        return a.equals(b);
    }
}
