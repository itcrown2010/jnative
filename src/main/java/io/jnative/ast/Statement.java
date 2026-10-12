/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

/**
 * @author 张晓达
 */
public sealed interface Statement extends AstNode
        permits BlockStatement, VariableDeclaration, ExpressionStatement,
        IfStatement, WhileStatement, DoWhileStatement, ForStatement,
        EnhancedForStatement,
        SwitchStatement,
        BreakStatement, ContinueStatement,
        ReturnStatement, EmptyStatement,
        ConstructorDelegationStatement, TryStatement, ThrowStatement {
}
