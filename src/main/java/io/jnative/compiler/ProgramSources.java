package io.jnative.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered list of independent source units for a program compile.
 * Order must not affect name resolution; it only affects deterministic IR emission.
 */
public final class ProgramSources {
    private final List<SourceUnit> units;

    public ProgramSources(List<SourceUnit> units) {
        this.units = List.copyOf(Objects.requireNonNullElse(units, List.of()));
    }

    public static ProgramSources of(SourceUnit... units) {
        return new ProgramSources(List.of(units));
    }

    public static ProgramSources of(List<SourceUnit> units) {
        return new ProgramSources(units);
    }

    public List<SourceUnit> units() {
        return units;
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    /** Concatenate texts only for stdlib need-detection probes (never for parsing). */
    public String probeText() {
        StringBuilder sb = new StringBuilder();
        for (SourceUnit u : units) {
            sb.append(u.sourceText()).append('\n');
        }
        return sb.toString();
    }

    public ProgramSources prepend(List<SourceUnit> prefix) {
        List<SourceUnit> out = new ArrayList<>(prefix.size() + units.size());
        out.addAll(prefix);
        out.addAll(units);
        return new ProgramSources(out);
    }

    public ProgramSources append(List<SourceUnit> suffix) {
        List<SourceUnit> out = new ArrayList<>(units.size() + suffix.size());
        out.addAll(units);
        out.addAll(suffix);
        return new ProgramSources(out);
    }
}
