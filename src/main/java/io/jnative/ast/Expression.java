/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

/**
 * @author 张晓达
 */
public sealed interface Expression extends AstNode
        permits BinaryExpression, UnaryExpression, AssignmentExpression,
        LiteralExpression, VariableExpression, MethodCallExpression,
        PrintlnExpression, GroupingExpression,
        NewObjectExpression, FieldAccessExpression, NullLiteralExpression, ThisExpression, SuperExpression,
        NewArrayExpression, ArrayInitExpression, ArrayAccessExpression, InstanceOfExpression, CastExpression,
        ConditionalExpression,
        GcCollectExpression, NativeNullExpression,
        LambdaExpression, MethodReferenceExpression,
        AwaitExpression, SwitchExpression {
}
