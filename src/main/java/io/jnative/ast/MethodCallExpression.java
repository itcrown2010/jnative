package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * Method invocation. {@code receiver == null} means static / unqualified call.
 */
public record MethodCallExpression(
        Expression receiver,
        String name,
        List<Expression> arguments,
        SourceLocation location
) implements Expression {
}
