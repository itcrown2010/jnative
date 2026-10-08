package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Single-type import {@code import com.acme.Foo;}.
 * Static and wildcard imports are rejected by the parser with diagnostics.
 */
public record ImportDeclaration(
        String qualifiedType,
        SourceLocation location
) {
    public String simpleName() {
        if (qualifiedType == null) {
            return "";
        }
        int dot = qualifiedType.lastIndexOf('.');
        return dot < 0 ? qualifiedType : qualifiedType.substring(dot + 1);
    }
}
