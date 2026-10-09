package io.jnative.opt;

/** Escape classification for allocated objects (intraprocedural).
 * @author 张晓达
 */
public record EscapeState(EscapeKind kind, String exactClass) {
    public enum EscapeKind {
        NO_ESCAPE,
        ARG_ESCAPE,
        RETURN_ESCAPE,
        GLOBAL_ESCAPE,
        THREAD_ESCAPE,
        NATIVE_ESCAPE,
        UNKNOWN
    }

    public static EscapeState noEscape(String exactClass) {
        return new EscapeState(EscapeKind.NO_ESCAPE, exactClass);
    }

    public static EscapeState of(EscapeKind kind) {
        return new EscapeState(kind, null);
    }

    public static EscapeState of(EscapeKind kind, String exactClass) {
        return new EscapeState(kind, exactClass);
    }

    public boolean isNoEscape() {
        return kind == EscapeKind.NO_ESCAPE;
    }

    public EscapeState escalate(EscapeKind other) {
        if (other == null || other == EscapeKind.NO_ESCAPE) {
            return this;
        }
        if (kind == EscapeKind.NO_ESCAPE) {
            return new EscapeState(other, exactClass);
        }
        if (kind == other) {
            return this;
        }
        return new EscapeState(EscapeKind.UNKNOWN, exactClass);
    }
}
