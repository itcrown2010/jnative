package io.jnative.diagnostics;

/**
 * Source position used by diagnostics throughout the compiler.
 */
public record SourceLocation(String file, int line, int column) {
    public static final SourceLocation UNKNOWN = new SourceLocation("<unknown>", 0, 0);

    public String format() {
        return file + ":" + line + ":" + column;
    }
}
