package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record CatchClause(
        TypeName exceptionType,
        String variableName,
        BlockStatement body,
        SourceLocation location
) {
}
