package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * {@code Type::method} or {@code expr::method}.
 * When {@code typeName != null}, this is an unbound/static reference on a type.
 * When {@code receiver != null}, this is a bound instance reference.
 */
public record MethodReferenceExpression(
        TypeName typeName,
        Expression receiver,
        String methodName,
        SourceLocation location
) implements Expression {
}
