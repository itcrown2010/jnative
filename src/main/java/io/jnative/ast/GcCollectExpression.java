package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * System.gc() — JNative 0.5 implementation-specific intrinsic triggering a full GC.
 */
public record GcCollectExpression(SourceLocation location) implements Expression {
}
