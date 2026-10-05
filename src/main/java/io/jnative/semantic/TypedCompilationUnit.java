package io.jnative.semantic;

import io.jnative.ast.CatchClause;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.Expression;

import java.util.Map;

/**
 * Result of semantic analysis: AST plus resolved classes and expression types.
 */
public record TypedCompilationUnit(
        CompilationUnit unit,
        Map<String, ClassSymbol> classes,
        Map<Expression, JType> expressionTypes,
        Map<Expression, Object> resolvedSymbols,
        Map<String, ClassMetadata> arrayMetadatas,
        Map<CatchClause, ClassSymbol> catchTypes
) {
    public ClassSymbol requireClass(String name) {
        ClassSymbol symbol = classes.get(name);
        if (symbol == null) {
            throw new IllegalStateException("unknown class: " + name);
        }
        return symbol;
    }
}
