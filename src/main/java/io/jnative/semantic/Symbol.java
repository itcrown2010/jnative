package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

/**
 * Legacy sealed Symbol kept for Scope storage of variables and methods.
 * Prefer typed ClassSymbol / FieldSymbol / MethodSymbol / VariableSymbol APIs.
 * @author 张晓达
 */
public sealed interface Symbol permits Symbol.VariableBinding, Symbol.MethodBinding {

    String name();

    SourceLocation location();

    record VariableBinding(VariableSymbol variable) implements Symbol {
        @Override
        public String name() {
            return variable.name();
        }

        @Override
        public SourceLocation location() {
            return variable.location();
        }
    }

    record MethodBinding(MethodSymbol method) implements Symbol {
        @Override
        public String name() {
            return method.name();
        }

        @Override
        public SourceLocation location() {
            return method.location();
        }
    }
}
