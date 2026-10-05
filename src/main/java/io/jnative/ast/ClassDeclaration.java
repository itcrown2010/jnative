package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record ClassDeclaration(
        String name,
        String superClassName,
        List<FieldDeclaration> fields,
        List<MethodDeclaration> methods,
        SourceLocation location
) implements AstNode {
}
