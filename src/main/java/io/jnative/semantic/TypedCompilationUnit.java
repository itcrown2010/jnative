package io.jnative.semantic;

import io.jnative.ast.CatchClause;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.Expression;
import io.jnative.ast.MethodCallExpression;
import io.jnative.ast.ProgramAst;
import io.jnative.ast.TryStatement;
import io.jnative.ffi.TargetAbi;

import java.util.List;
import java.util.Map;

/**
 * Result of semantic analysis: one or more ASTs plus resolved classes/interfaces and expression types.
 * {@link #unit()} is a flattened view for IR/backends; {@link #units()} retains per-file ASTs.
 */
public record TypedCompilationUnit(
        CompilationUnit unit,
        List<CompilationUnit> units,
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
    public TypedCompilationUnit {
        units = units == null || units.isEmpty()
                ? List.of(unit)
                : List.copyOf(units);
    }

    /** Legacy single-unit constructor. */
    public TypedCompilationUnit(
            CompilationUnit unit,
            Map<String, ClassSymbol> classes,
            Map<String, InterfaceSymbol> interfaces,
            Map<String, NativeStructSymbol> nativeStructs,
            Map<Expression, JType> expressionTypes,
            Map<Expression, Object> resolvedSymbols,
            Map<Expression, CallableBinding> callableBindings,
            Map<String, ClassMetadata> arrayMetadatas,
            Map<CatchClause, ClassSymbol> catchTypes,
            Map<TryStatement, List<MethodCallExpression>> resourceCloseCalls,
            List<String> nativeLibraries,
            TargetAbi targetAbi
    ) {
        this(unit, List.of(unit), classes, interfaces, nativeStructs, expressionTypes,
                resolvedSymbols, callableBindings, arrayMetadatas, catchTypes, resourceCloseCalls,
                nativeLibraries, targetAbi);
    }

    public ProgramAst program() {
        return new ProgramAst(units);
    }

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
