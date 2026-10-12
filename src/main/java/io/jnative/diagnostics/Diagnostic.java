/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.diagnostics;

import java.util.List;

/**
 * Compiler diagnostic shared by CLI, JSON reports, and IDE Problems.
 *
 * @author 张晓达
 */
public record Diagnostic(
        Severity severity,
        SourceLocation location,
        String message,
        String code,
        String hint,
        List<RelatedLocation> related
) {

    public enum Severity {
        ERROR,
        WARNING,
        NOTE
    }

    /** Secondary location (e.g. conflicting definition, package manifest). */
    public record RelatedLocation(SourceLocation location, String message) {
        public RelatedLocation {
            if (location == null) {
                location = SourceLocation.UNKNOWN;
            }
            if (message == null) {
                message = "";
            }
        }
    }

    public Diagnostic {
        if (related == null) {
            related = List.of();
        } else {
            related = List.copyOf(related);
        }
    }

    /** Backward-compatible constructor used throughout the compiler. */
    public Diagnostic(Severity severity, SourceLocation location, String message) {
        this(severity, location, message, null, null, List.of());
    }

    public static Diagnostic error(SourceLocation location, String message) {
        return new Diagnostic(Severity.ERROR, location, message, null, null, List.of());
    }

    public static Diagnostic warning(SourceLocation location, String message) {
        return new Diagnostic(Severity.WARNING, location, message, null, null, List.of());
    }

    public static Diagnostic note(SourceLocation location, String message) {
        return new Diagnostic(Severity.NOTE, location, message, null, null, List.of());
    }

    public static Diagnostic error(
            String code,
            SourceLocation location,
            String message,
            String hint
    ) {
        return new Diagnostic(Severity.ERROR, location, message, code, hint, List.of());
    }

    public static Diagnostic error(
            String code,
            SourceLocation location,
            String message,
            String hint,
            List<RelatedLocation> related
    ) {
        return new Diagnostic(Severity.ERROR, location, message, code, hint, related);
    }

    public Diagnostic withCode(String newCode) {
        return new Diagnostic(severity, location, message, newCode, hint, related);
    }

    public Diagnostic withHint(String newHint) {
        return new Diagnostic(severity, location, message, code, newHint, related);
    }

    public Diagnostic withRelated(List<RelatedLocation> newRelated) {
        return new Diagnostic(severity, location, message, code, hint, newRelated);
    }

    @Override
    public String toString() {
        String sev = switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case NOTE -> "note";
        };
        StringBuilder sb = new StringBuilder();
        sb.append(location == null ? SourceLocation.UNKNOWN.format() : location.format());
        sb.append(": ").append(sev).append(": ");
        if (code != null && !code.isBlank()) {
            sb.append('[').append(code).append("] ");
        }
        sb.append(message);
        if (hint != null && !hint.isBlank()) {
            sb.append("\n  help: ").append(hint);
        }
        if (related != null) {
            for (RelatedLocation r : related) {
                sb.append("\n  --> ").append(r.location().format());
                if (r.message() != null && !r.message().isBlank()) {
                    sb.append(": ").append(r.message());
                }
            }
        }
        return sb.toString();
    }
}
