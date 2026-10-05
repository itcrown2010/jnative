package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record TryStatement(
        BlockStatement tryBlock,
        List<CatchClause> catches,
        BlockStatement finallyBlock,
        SourceLocation location
) implements Statement {
}
