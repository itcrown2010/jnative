package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ThisExpression(SourceLocation location) implements Expression {
}
