package io.jnative.ast;

public sealed interface AstNode
        permits CompilationUnit, ClassDeclaration, MethodDeclaration, TypeParameterDeclaration,
        Parameter, FieldDeclaration, Statement, Expression, LambdaParameter {
    io.jnative.diagnostics.SourceLocation location();
}
