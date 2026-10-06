package io.jnative.ir;

import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.TypedCompilationUnit;

/**
 * Textual report of async/await sites for {@code --emit-async}.
 */
public final class AsyncReport {

    private AsyncReport() {
    }

    public static String emit(IrModule module, TypedCompilationUnit typed) {
        StringBuilder sb = new StringBuilder();
        sb.append("; JNative 0.23 async/await report\n");
        for (IrFunction function : module.functions()) {
            if (!function.isAsync() && !hasAsyncMarkers(function)) {
                continue;
            }
            sb.append("async func ").append(function.name()).append('\n');
            for (IrBasicBlock block : function.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    switch (inst) {
                        case IrInstruction.AsyncBegin b ->
                                sb.append("  [begin] ").append(b.methodId())
                                        .append(" result=").append(b.resultType()).append('\n');
                        case IrInstruction.Await a ->
                                sb.append("  [await] state=").append(a.resumeState())
                                        .append(" resume=").append(a.resumeLabel())
                                        .append(" type=").append(a.resultType()).append('\n');
                        case IrInstruction.AsyncComplete c ->
                                sb.append("  [complete] type=").append(c.resultType()).append('\n');
                        case IrInstruction.AsyncSuspend s ->
                                sb.append("  [suspend] state=").append(s.resumeState()).append('\n');
                        default -> {
                        }
                    }
                }
            }
        }
        int asyncMethods = 0;
        for (var cls : typed.classes().values()) {
            for (MethodSymbol ms : cls.declaredMethods()) {
                if (ms.isAsync()) {
                    asyncMethods++;
                    sb.append("method ").append(cls.name()).append('.').append(ms.name())
                            .append(" logical=").append(ms.logicalReturnType().displayName())
                            .append(" lowered=").append(ms.returnType().displayName())
                            .append('\n');
                }
            }
        }
        sb.append("; total async methods: ").append(asyncMethods).append('\n');
        return sb.toString();
    }

    private static boolean hasAsyncMarkers(IrFunction function) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.AsyncBegin
                        || inst instanceof IrInstruction.Await) {
                    return true;
                }
            }
        }
        return false;
    }
}
