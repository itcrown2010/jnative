package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record VariableExpression(String name, SourceLocation location) implements Expression {
}
