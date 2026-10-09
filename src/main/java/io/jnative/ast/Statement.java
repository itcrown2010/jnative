package io.jnative.ast;

public sealed interface Statement extends AstNode
        permits BlockStatement, VariableDeclaration, ExpressionStatement,
        IfStatement, WhileStatement, DoWhileStatement, ForStatement,
        EnhancedForStatement,
        SwitchStatement,
        BreakStatement, ContinueStatement,
        ReturnStatement, EmptyStatement,
        ConstructorDelegationStatement, TryStatement, ThrowStatement {
}
