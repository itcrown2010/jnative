package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Declared effect contracts on a method: {@code @NoGC}, {@code @NoThrow}, {@code @NoBlock},
 * {@code @Deterministic}.
 */
public record EffectContract(
        boolean noGc,
        boolean noThrow,
        boolean noBlock,
        boolean deterministic,
        SourceLocation location
) {
    public EffectContract merge(EffectContract other) {
        if (other == null) {
            return this;
        }
        boolean det = deterministic || other.deterministic;
        return new EffectContract(
                noGc || other.noGc || det,
                noThrow || other.noThrow || det,
                noBlock || other.noBlock || det,
                det,
                location != null ? location : other.location);
    }

    public static EffectContract empty(SourceLocation loc) {
        return new EffectContract(false, false, false, false, loc);
    }
}
