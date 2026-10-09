package io.jnative.compiler;

import io.jnative.ast.CompilationUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.ir.IrModule;

import java.nio.file.Path;
import java.util.List;

/**
 * @author 张晓达
 */
public record CompileResult(
        boolean success,
        CompilationUnit ast,
        IrModule ir,
        String llvmIr,
        Path executable,
        List<Diagnostic> diagnostics
) {
}
