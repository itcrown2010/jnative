package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * {@code await expr} — cooperative suspension on a {@code Future<T>} (JNative 0.23).
 * @author 张晓达
 */
public record AwaitExpression(Expression operand, SourceLocation location) implements Expression {
}
