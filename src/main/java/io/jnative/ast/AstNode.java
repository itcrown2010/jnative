package io.jnative.ast;

public sealed interface AstNode
        permits CompilationUnit, ClassDeclaration, MethodDeclaration, Parameter,
        FieldDeclaration, Statement, Expression {
    io.jnative.diagnostics.SourceLocation location();
}
