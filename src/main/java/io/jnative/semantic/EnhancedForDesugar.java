package io.jnative.semantic;

import io.jnative.ast.MethodCallExpression;

/**
 * Desugared Iterable enhanced-for: {@code iterator()} once, then {@code hasNext}/{@code next} per iteration.
 * Array enhanced-for does not use this record (IR lowers arrays directly).
 */
public record EnhancedForDesugar(
        MethodCallExpression iteratorCall,
        MethodCallExpression hasNextCall,
        MethodCallExpression nextCall,
        String iteratorLocalName
) {
}
