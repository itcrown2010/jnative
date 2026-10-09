package io.jnative.compiler;

import io.jnative.ast.CompilationUnit;
import io.jnative.diagnostics.Diagnostic;

import java.util.List;

/**
 * Per-file syntax product for IDE analysis (frontend only).
 * @author 张晓达
 */
public final class SyntaxTree {
    private final String fileName;
    private final String source;
    private final CompilationUnit root;
    private final List<Diagnostic> diagnostics;
    private final long version;

    public SyntaxTree(
            String fileName,
            String source,
            CompilationUnit root,
            List<Diagnostic> diagnostics,
            long version) {
        this.fileName = fileName;
        this.source = source;
        this.root = root;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.version = version;
    }

    public String fileName() {
        return fileName;
    }

    public String source() {
        return source;
    }

    public CompilationUnit root() {
        return root;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public long version() {
        return version;
    }
}
