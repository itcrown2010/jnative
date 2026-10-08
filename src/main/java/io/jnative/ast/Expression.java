package io.jnative.ast;

public sealed interface Expression extends AstNode
        permits BinaryExpression, UnaryExpression, AssignmentExpression,
        LiteralExpression, VariableExpression, MethodCallExpression,
        PrintlnExpression, GroupingExpression,
        NewObjectExpression, FieldAccessExpression, NullLiteralExpression, ThisExpression,
        NewArrayExpression, ArrayAccessExpression, InstanceOfExpression, CastExpression,
        ConditionalExpression,
        GcCollectExpression, NativeNullExpression,
        LambdaExpression, MethodReferenceExpression,
        AwaitExpression {
}
