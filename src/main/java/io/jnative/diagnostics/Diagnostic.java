package io.jnative.diagnostics;

/**
 * @author 张晓达
 */
public record Diagnostic(Severity severity, SourceLocation location, String message) {

    public enum Severity {
        ERROR,
        WARNING,
        NOTE
    }

    public static Diagnostic error(SourceLocation location, String message) {
        return new Diagnostic(Severity.ERROR, location, message);
    }

    public static Diagnostic warning(SourceLocation location, String message) {
        return new Diagnostic(Severity.WARNING, location, message);
    }

    public static Diagnostic note(SourceLocation location, String message) {
        return new Diagnostic(Severity.NOTE, location, message);
    }

    @Override
    public String toString() {
        String sev = switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case NOTE -> "note";
        };
        return location.format() + ": " + sev + ": " + message;
    }
}
