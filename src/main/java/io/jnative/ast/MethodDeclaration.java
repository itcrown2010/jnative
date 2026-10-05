package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record MethodDeclaration(
        boolean isPublic,
        boolean isStatic,
        boolean isConstructor,
        TypeName returnType,
        String name,
        List<Parameter> parameters,
        BlockStatement body,
        SourceLocation location
) implements AstNode {
}
