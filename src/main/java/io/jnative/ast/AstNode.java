/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

/**
 * @author 张晓达
 */
public sealed interface AstNode
        permits CompilationUnit, ClassDeclaration, MethodDeclaration, TypeParameterDeclaration,
        Parameter, FieldDeclaration, Statement, Expression, LambdaParameter {
    io.jnative.diagnostics.SourceLocation location();
}
