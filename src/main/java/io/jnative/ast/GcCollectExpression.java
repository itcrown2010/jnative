package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * System.gc() — JNative 0.5 implementation-specific intrinsic triggering a full GC.
 * @author 张晓达
 */
public record GcCollectExpression(SourceLocation location) implements Expression {
}
