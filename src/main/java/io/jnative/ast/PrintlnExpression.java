package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Special-cased System.out.println(...) for JNative 0.1.
 */
public record PrintlnExpression(Expression argument, SourceLocation location) implements Expression {
}
