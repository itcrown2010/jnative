package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record NullLiteralExpression(SourceLocation location) implements Expression {
}
