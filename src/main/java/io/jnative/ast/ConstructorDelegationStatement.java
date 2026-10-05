package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** this(...) or super(...) as the first statement of a constructor. */
public record ConstructorDelegationStatement(
        boolean isSuper,
        List<Expression> arguments,
        SourceLocation location
) implements Statement {
}
