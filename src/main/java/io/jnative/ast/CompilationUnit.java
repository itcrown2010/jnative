package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record CompilationUnit(
        String fileName,
        List<ClassDeclaration> classes,
        SourceLocation location
) implements AstNode {
}
