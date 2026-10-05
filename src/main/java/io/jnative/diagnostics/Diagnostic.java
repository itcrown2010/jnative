package io.jnative.diagnostics;

public record Diagnostic(Severity severity, SourceLocation location, String message) {

    public enum Severity {
        ERROR,
        WARNING,
        NOTE
    }

    public static Diagnostic error(SourceLocation location, String message) {
        return new Diagnostic(Severity.ERROR, location, message);
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
