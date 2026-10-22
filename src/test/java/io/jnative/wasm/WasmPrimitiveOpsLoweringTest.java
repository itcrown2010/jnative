/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.wasm;

import io.jnative.backend.wasm.BrowserWatEmitter;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.ir.IrBuilder;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import io.jnative.ir.IrValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WASM lowering for new integer ops; browser target must reject println clearly.
 * @author 张晓达
 */
class WasmPrimitiveOpsLoweringTest {

    @Test
    void watEmitsBitwiseAndShifts() {
        IrModule module = new IrModule("ops");
        IrFunction fn = new IrFunction("jn_main", IrType.I32, List.of(), true);
        module.addFunction(fn);
        IrBuilder b = new IrBuilder(module);
        b.beginFunction(fn);
        IrValue a = new IrValue.Const(IrType.I32, 6);
        IrValue c = new IrValue.Const(IrType.I32, 3);
        IrValue and = b.binary(IrInstruction.BinOp.AND, a, c);
        IrValue xor = b.binary(IrInstruction.BinOp.XOR, a, c);
        IrValue sh = b.binary(IrInstruction.BinOp.SHL, a, new IrValue.Const(IrType.I32, 1));
        IrValue ashr = b.binary(IrInstruction.BinOp.ASHR, new IrValue.Const(IrType.I32, -8),
                new IrValue.Const(IrType.I32, 2));
        IrValue lshr = b.binary(IrInstruction.BinOp.LSHR, new IrValue.Const(IrType.I32, -8),
                new IrValue.Const(IrType.I32, 2));
        IrValue sum1 = b.binary(IrInstruction.BinOp.ADD, and, xor);
        IrValue sum2 = b.binary(IrInstruction.BinOp.ADD, sh, ashr);
        IrValue sum3 = b.binary(IrInstruction.BinOp.ADD, sum1, sum2);
        b.ret(b.binary(IrInstruction.BinOp.ADD, sum3, lshr));

        String wat = new BrowserWatEmitter().emit(module);
        assertTrue(wat.contains("i32.and"), wat);
        assertTrue(wat.contains("i32.xor"), wat);
        assertTrue(wat.contains("i32.shl"), wat);
        assertTrue(wat.contains("i32.shr_s"), wat);
        assertTrue(wat.contains("i32.shr_u"), wat);
    }

    @Test
    void browserTargetCompilesPrintln(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.jn");
        Files.writeString(src, """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(1 << 2);
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(src, Files.readString(src))));
        options.setOutputFile(dir.resolve("out"));
        options.setTarget("wasm32-browser");
        CompileResult cr = new JNativeCompiler().compile(options);
        // println lowers to the host console import on wasm32-browser now.
        String msg = cr.diagnostics().stream().map(Object::toString).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(cr.success(), msg);
    }
}
