package io.jnative.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves overloaded callables by exact then widening match.
 */
public final class CallableResolver {

    public record Candidate(List<JType> parameterTypes, Object symbol) {
    }

    private CallableResolver() {
    }

    public static Object resolve(List<Candidate> candidates, List<JType> argTypes) {
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> compatible = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.parameterTypes().size() != argTypes.size()) {
                continue;
            }
            boolean exactMatch = true;
            boolean ok = true;
            for (int i = 0; i < argTypes.size(); i++) {
                JType expected = c.parameterTypes().get(i);
                JType actual = argTypes.get(i);
                if (!expected.isAssignableFrom(actual)) {
                    ok = false;
                    break;
                }
                if (!sameType(expected, actual)) {
                    exactMatch = false;
                }
            }
            if (!ok) {
                continue;
            }
            if (exactMatch) {
                exact.add(c);
            } else {
                compatible.add(c);
            }
        }
        if (exact.size() == 1) {
            return exact.getFirst().symbol();
        }
        if (exact.isEmpty() && compatible.size() == 1) {
            return compatible.getFirst().symbol();
        }
        return null; // ambiguous or none
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
