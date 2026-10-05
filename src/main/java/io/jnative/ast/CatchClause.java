package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record CatchClause(
        TypeName exceptionType,
        String variableName,
        BlockStatement body,
        SourceLocation location
) {
}
