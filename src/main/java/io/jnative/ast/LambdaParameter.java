package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** Parameter in a lambda; type may be inferred from target typing.
 * @author 张晓达
 */
public record LambdaParameter(
        TypeName type,
        boolean isArray,
        String name,
        SourceLocation location
) implements AstNode {
    public LambdaParameter(String name, SourceLocation location) {
        this(null, false, name, location);
    }
}
