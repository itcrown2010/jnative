package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record VariableExpression(String name, SourceLocation location) implements Expression {
}
