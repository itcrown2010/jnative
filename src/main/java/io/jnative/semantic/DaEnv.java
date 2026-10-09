package io.jnative.semantic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Definite-assignment / definite-unassignment facts for local variables and parameters.
 * Intersection merge: a variable is DA (DU) after a join only if it is DA (DU) on every incoming path.
 */
final class DaEnv {
    private final IdentityHashMap<VariableSymbol, Facts> facts = new IdentityHashMap<>();

    record Facts(boolean assigned, boolean unassigned) {
        public static Facts ofAssigned() {
            return new Facts(true, false);
        }

        public static Facts ofUnassigned() {
            return new Facts(false, true);
        }

        public static Facts neither() {
            return new Facts(false, false);
        }

        public Facts merge(Facts other) {
            return new Facts(assigned && other.assigned, unassigned && other.unassigned);
        }
    }

    DaEnv copy() {
        DaEnv c = new DaEnv();
        c.facts.putAll(facts);
        return c;
    }

    void declare(VariableSymbol vs, boolean initiallyAssigned) {
        facts.put(vs, initiallyAssigned ? Facts.ofAssigned() : Facts.ofUnassigned());
    }

    void remove(VariableSymbol vs) {
        facts.remove(vs);
    }

    boolean tracks(VariableSymbol vs) {
        return facts.containsKey(vs);
    }

    boolean isAssigned(VariableSymbol vs) {
        Facts f = facts.get(vs);
        return f != null && f.assigned;
    }

    boolean isUnassigned(VariableSymbol vs) {
        Facts f = facts.get(vs);
        return f != null && f.unassigned;
    }

    void markAssigned(VariableSymbol vs) {
        if (facts.containsKey(vs)) {
            facts.put(vs, Facts.ofAssigned());
        }
    }

    /** Intersection merge into this from {@code other}. */
    void mergeFrom(DaEnv other) {
        Set<VariableSymbol> keys = Collections.newSetFromMap(new IdentityHashMap<>());
        keys.addAll(facts.keySet());
        keys.addAll(other.facts.keySet());
        for (VariableSymbol vs : keys) {
            Facts a = facts.get(vs);
            Facts b = other.facts.get(vs);
            if (a == null && b == null) {
                continue;
            }
            if (a == null) {
                // Variable not in this path (declared in other branch) — drop.
                continue;
            }
            if (b == null) {
                // Declared only on this path — after join, not in scope together; keep for same-block? Drop.
                facts.remove(vs);
                continue;
            }
            facts.put(vs, a.merge(b));
        }
    }

    static DaEnv mergeAll(Iterable<DaEnv> envs) {
        DaEnv result = null;
        for (DaEnv e : envs) {
            if (e == null) {
                continue;
            }
            if (result == null) {
                result = e.copy();
            } else {
                result.mergeFrom(e);
            }
        }
        return result == null ? new DaEnv() : result;
    }

    Map<VariableSymbol, Facts> snapshot() {
        return Map.copyOf(facts);
    }
}
