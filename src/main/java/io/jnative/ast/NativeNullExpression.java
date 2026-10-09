package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Native null pointer literal ({@code NativePtr.NULL}). Distinct from Java {@code null}.
 * @author 张晓达
 */
public record NativeNullExpression(SourceLocation location) implements Expression {
}
