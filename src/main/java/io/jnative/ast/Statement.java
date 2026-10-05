package io.jnative.ast;

public sealed interface Statement extends AstNode
        permits BlockStatement, VariableDeclaration, ExpressionStatement,
        IfStatement, WhileStatement, ForStatement, ReturnStatement, EmptyStatement,
        ConstructorDelegationStatement, TryStatement, ThrowStatement {
}
