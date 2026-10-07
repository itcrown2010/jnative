package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record TryStatement(
        List<VariableDeclaration> resources,
        BlockStatement tryBlock,
        List<CatchClause> catches,
        BlockStatement finallyBlock,
        SourceLocation location
) implements Statement {
    public TryStatement(
            BlockStatement tryBlock,
            List<CatchClause> catches,
            BlockStatement finallyBlock,
            SourceLocation location
    ) {
        this(List.of(), tryBlock, catches, finallyBlock, location);
    }
}
