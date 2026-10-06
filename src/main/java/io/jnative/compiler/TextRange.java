package io.jnative.compiler;

import io.jnative.diagnostics.SourceLocation;

/**
 * Stable source range for IDE navigation (line/column, 1-based like {@link SourceLocation}).
 */
public record TextRange(String file, int startLine, int startColumn, int endLine, int endColumn) {

    public static TextRange of(SourceLocation loc) {
        if (loc == null) {
            return new TextRange("", 0, 0, 0, 0);
        }
        return new TextRange(loc.file(), loc.line(), loc.column(), loc.line(), loc.column());
    }

    public boolean contains(int line, int column) {
        if (line < startLine || line > endLine) {
            return false;
        }
        if (line == startLine && column < startColumn) {
            return false;
        }
        if (line == endLine && endColumn > 0 && column > endColumn) {
            return false;
        }
        return true;
    }
}
