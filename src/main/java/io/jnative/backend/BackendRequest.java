package io.jnative.backend;

import io.jnative.compiler.CompilerOptions;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.ir.IrModule;
import io.jnative.semantic.TypedCompilationUnit;

import java.nio.file.Path;
import java.util.List;

/** Inputs shared by native and WASM backends after frontend/IR. */
public record BackendRequest(
        Path sourceFile,
        String sourceText,
        TypedCompilationUnit typed,
        IrModule ir,
        CompilerOptions options,
        Path outputDir
) {
}
