package io.jnative.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects compiler diagnostics. Never throws for semantic/syntax errors.
 */
public final class DiagnosticReporter {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void error(SourceLocation location, String message) {
        diagnostics.add(Diagnostic.error(location, message));
    }

    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public void printAll() {
        for (Diagnostic d : diagnostics) {
            System.err.println(d);
        }
    }

    public int errorCount() {
        int count = 0;
        for (Diagnostic d : diagnostics) {
            if (d.severity() == Diagnostic.Severity.ERROR) {
                count++;
            }
        }
        return count;
    }
}
