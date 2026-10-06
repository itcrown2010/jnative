package io.jnative.pkg;

import java.util.ArrayList;
import java.util.List;

/**
 * Version constraint: exact, ^, ~, >=, ranges, *.
 */
public final class VersionConstraint {
    private final String raw;
    private final List<Clause> clauses;

    private VersionConstraint(String raw, List<Clause> clauses) {
        this.raw = raw;
        this.clauses = clauses;
    }

    public static VersionConstraint parse(String text) {
        if (text == null || text.isBlank() || "*".equals(text.trim())) {
            return new VersionConstraint("*", List.of(v -> true));
        }
        String raw = text.trim();
        String[] parts = raw.split("\\s*,\\s*|\\s+");
        List<Clause> all = new ArrayList<>();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            all.add(parseClause(p.trim()));
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("empty constraint: " + text);
        }
        return new VersionConstraint(raw, all);
    }

    public String raw() {
        return raw;
    }

    public boolean matches(SemanticVersion v) {
        for (Clause c : clauses) {
            if (!c.matches(v)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return raw;
    }

    @FunctionalInterface
    private interface Clause {
        boolean matches(SemanticVersion v);
    }

    private static Clause parseClause(String p) {
        String s = p.trim();
        if ("*".equals(s)) {
            return v -> true;
        }
        if (s.startsWith("^")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(1));
            SemanticVersion upper = base.major() == 0
                    ? (base.minor() == 0
                    ? new SemanticVersion(0, 0, base.patch() + 1)
                    : new SemanticVersion(0, base.minor() + 1, 0))
                    : base.nextMajor();
            return v -> v.compareTo(base) >= 0 && v.compareTo(upper) < 0;
        }
        if (s.startsWith("~")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(1));
            SemanticVersion upper = base.nextMinor();
            return v -> v.compareTo(base) >= 0 && v.compareTo(upper) < 0;
        }
        if (s.startsWith(">=")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(2).trim());
            return v -> v.compareTo(base) >= 0;
        }
        if (s.startsWith("<=")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(2).trim());
            return v -> v.compareTo(base) <= 0;
        }
        if (s.startsWith(">")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(1).trim());
            return v -> v.compareTo(base) > 0;
        }
        if (s.startsWith("<")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(1).trim());
            return v -> v.compareTo(base) < 0;
        }
        if (s.startsWith("=")) {
            SemanticVersion base = SemanticVersion.parse(s.substring(1).trim());
            return v -> v.equals(base);
        }
        SemanticVersion exact = SemanticVersion.parse(s);
        return v -> v.equals(exact);
    }
}
