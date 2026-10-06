package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record NewObjectExpression(
        String className,
        List<TypeName> typeArguments,
        boolean diamond,
        List<Expression> arguments,
        SourceLocation location
) implements Expression {
    public NewObjectExpression(String className, List<Expression> arguments, SourceLocation location) {
        this(className, List.of(), false, arguments, location);
    }
}
