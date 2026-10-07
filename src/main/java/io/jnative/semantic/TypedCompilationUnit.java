package io.jnative.semantic;

import io.jnative.ast.CatchClause;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.Expression;
import io.jnative.ast.MethodCallExpression;
import io.jnative.ast.TryStatement;
import io.jnative.ffi.TargetAbi;

import java.util.List;
import java.util.Map;

/**
 * Result of semantic analysis: AST plus resolved classes/interfaces and expression types.
 */
public record TypedCompilationUnit(
        CompilationUnit unit,
        Map<String, ClassSymbol> classes,
        Map<String, InterfaceSymbol> interfaces,
        Map<String, NativeStructSymbol> nativeStructs,
        Map<Expression, JType> expressionTypes,
        Map<Expression, Object> resolvedSymbols,
        Map<Expression, CallableBinding> callableBindings,
        Map<String, ClassMetadata> arrayMetadatas,
        Map<CatchClause, ClassSymbol> catchTypes,
        /** Synthetic {@code close()} calls for try-with-resources, already in reverse close order. */
        Map<TryStatement, List<MethodCallExpression>> resourceCloseCalls,
        List<String> nativeLibraries,
        TargetAbi targetAbi
) {
    public ClassSymbol requireClass(String name) {
        ClassSymbol symbol = classes.get(name);
        if (symbol == null) {
            throw new IllegalStateException("unknown class: " + name);
        }
        return symbol;
    }

    public InterfaceSymbol requireInterface(String name) {
        InterfaceSymbol symbol = interfaces.get(name);
        if (symbol == null) {
            throw new IllegalStateException("unknown interface: " + name);
        }
        return symbol;
    }

    public NativeStructSymbol requireNativeStruct(String name) {
        NativeStructSymbol symbol = nativeStructs.get(name);
        if (symbol == null) {
            throw new IllegalStateException("unknown native struct: " + name);
        }
        return symbol;
    }
}
