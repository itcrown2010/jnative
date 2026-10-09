package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * @author 张晓达
 */
public record BlockStatement(List<Statement> statements, SourceLocation location) implements Statement {
}
