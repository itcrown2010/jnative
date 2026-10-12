/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.compiler;

import io.jnative.diagnostics.Diagnostic;
import io.jnative.semantic.TypedCompilationUnit;

import java.util.List;

/**
 * Immutable frontend compilation snapshot for IDE consumption.
 * Stops before IR/LLVM — suitable for analysis / navigation / completion.
 * @author 张晓达
 */
public final class Compilation {
    private final long version;
    private final SyntaxTree syntaxTree;
    private final TypedCompilationUnit typed;
    private final SemanticModel semanticModel;
    private final List<Diagnostic> diagnostics;
    private final boolean success;
    private final long parseMillis;
    private final long semanticMillis;

    public Compilation(
            long version,
            SyntaxTree syntaxTree,
            TypedCompilationUnit typed,
            SemanticModel semanticModel,
            List<Diagnostic> diagnostics,
            boolean success,
            long parseMillis,
            long semanticMillis) {
        this.version = version;
        this.syntaxTree = syntaxTree;
        this.typed = typed;
        this.semanticModel = semanticModel;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.success = success;
        this.parseMillis = parseMillis;
        this.semanticMillis = semanticMillis;
    }

    public long version() {
        return version;
    }

    public SyntaxTree syntaxTree() {
        return syntaxTree;
    }

    public TypedCompilationUnit typed() {
        return typed;
    }

    public SemanticModel semanticModel() {
        return semanticModel;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean success() {
        return success;
    }

    public long parseMillis() {
        return parseMillis;
    }

    public long semanticMillis() {
        return semanticMillis;
    }
}
